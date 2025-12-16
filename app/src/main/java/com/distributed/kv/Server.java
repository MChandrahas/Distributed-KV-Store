package com.distributed.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
    
    private static final BPlusTree storage = new BPlusTree(128);
    private static final List<KVStoreGrpc.KVStoreBlockingStub> peers = new ArrayList<>();
    private static WAL wal;
    private static RaftNode raft;
    
    // Background threads for Raft
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final Random random = new Random();

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Setup ID and WAL
        String nodeId = System.getenv("HOSTNAME");
        if (nodeId == null) nodeId = "localhost"; // Fallback for local testing
        
        String dataDir = "kv-data";
        new java.io.File(dataDir).mkdirs();
        wal = new WAL(dataDir + "/" + nodeId + ".log");
        wal.replay(storage);

        raft = new RaftNode(nodeId);

        // 2. Setup Peers (Other nodes)
        String peersEnv = System.getenv("PEERS");
        if (peersEnv != null && !peersEnv.isEmpty()) {
            for (String addr : peersEnv.split(",")) {
                String[] parts = addr.split(":");
                ManagedChannel channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                        .usePlaintext().build();
                peers.add(KVStoreGrpc.newBlockingStub(channel));
            }
        }

        // 3. Start Raft Background Tasks
        startElectionTimer();
        startHeartbeatSender();

        // 4. Start Server
        io.grpc.Server server = ServerBuilder.forPort(9090)
                .addService(new KVServiceImpl())
                .build()
                .start();

        System.out.println("Server started on Port 9090 as " + nodeId);
        server.awaitTermination();
    }

    // --- RAFT LOGIC ---

    private static void startElectionTimer() {
        scheduler.scheduleAtFixedRate(() -> {
            if (raft.getState() == RaftNode.State.LEADER) return;

            // CHANGE THIS: Increase timeout to 1000ms - 2000ms
            long timeout = 1000 + random.nextInt(1000); 
            if (System.currentTimeMillis() - raft.getLastHeartbeat() > timeout) {
                startElection();
            }
        }, 0, 200, TimeUnit.MILLISECONDS); // Check every 200ms
    }

    private static void startElection() {
        System.out.println("--- ELECTION TIMEOUT! Becoming Candidate ---");
        raft.setState(RaftNode.State.CANDIDATE);
        raft.setTerm(raft.getTerm() + 1);
        raft.voteFor(System.getenv("HOSTNAME"), raft.getTerm()); // Vote for self

        int votes = 1;
        for (KVStoreGrpc.KVStoreBlockingStub peer : peers) {
            try {
                KVStoreProto.VoteResponse resp = peer.requestVote(
                    KVStoreProto.VoteRequest.newBuilder()
                        .setTerm(raft.getTerm())
                        .setCandidateId(System.getenv("HOSTNAME"))
                        .build()
                );
                if (resp.getVoteGranted()) votes++;
            } catch (Exception e) { /* Peer is down */ }
        }

        if (votes > (peers.size() + 1) / 2) {
            raft.setState(RaftNode.State.LEADER);
            System.out.println(">>> I AM THE LEADER (Term " + raft.getTerm() + ") <<<");
        }
    }

    private static void startHeartbeatSender() {
        scheduler.scheduleAtFixedRate(() -> {
            if (raft.getState() != RaftNode.State.LEADER) return;

            for (KVStoreGrpc.KVStoreBlockingStub peer : peers) {
                try {
                    peer.appendEntries(KVStoreProto.AppendRequest.newBuilder()
                        .setTerm(raft.getTerm())
                        .setLeaderId(System.getenv("HOSTNAME"))
                        .build());
                } catch (Exception e) { /* Peer down */ }
            }
        }, 0, 200, TimeUnit.MILLISECONDS); // CHANGE THIS: Send every 200ms
    }

    // --- GRPC SERVICE ---

    static class KVServiceImpl extends KVStoreGrpc.KVStoreImplBase {
        
        // 1. Handle Writes (Only Leader allowed)
        @Override
        public void put(KVStoreProto.PutRequest req, StreamObserver<KVStoreProto.PutResponse> responseObserver) {
            if (raft.getState() != RaftNode.State.LEADER) {
                responseObserver.onError(new Exception("I am not the Leader"));
                return;
            }

            try {
                int key = Integer.parseInt(req.getKey());

                // 1. Leader Write (Local Persistence)
                wal.writeEntry(key, req.getValue());
                storage.insert(key, req.getValue());
                System.out.println("Leader applied: " + key);

                // 2. Replicate to Followers
                for (KVStoreGrpc.KVStoreBlockingStub peer : peers) {
                    try {
                        peer.appendEntries(KVStoreProto.AppendRequest.newBuilder()
                            .setTerm(raft.getTerm())
                            .setLeaderId(System.getenv("HOSTNAME"))
                            .setKey(req.getKey())     // <--- SENDING DATA
                            .setValue(req.getValue()) // <--- SENDING DATA
                            .build());
                    } catch (Exception e) {
                        System.err.println("Replication failed: " + e.getMessage());
                    }
                }
                
                responseObserver.onNext(KVStoreProto.PutResponse.newBuilder().setSuccess(true).build());
                responseObserver.onCompleted();

            } catch (Exception e) {
                responseObserver.onError(e);
            }
        }

        @Override
        public void get(KVStoreProto.GetRequest req, StreamObserver<KVStoreProto.GetResponse> responseObserver) {
            int key = Integer.parseInt(req.getKey());
            String val = storage.search(key);
            KVStoreProto.GetResponse.Builder builder = KVStoreProto.GetResponse.newBuilder();
            if (val != null) builder.setValue(val);
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        // 2. Handle Vote Request
        @Override
        public void requestVote(KVStoreProto.VoteRequest req, StreamObserver<KVStoreProto.VoteResponse> responseObserver) {
            boolean granted = raft.voteFor(req.getCandidateId(), req.getTerm());
            responseObserver.onNext(KVStoreProto.VoteResponse.newBuilder()
                .setTerm(raft.getTerm())
                .setVoteGranted(granted)
                .build());
            responseObserver.onCompleted();
        }

        // 3. Handle Heartbeat
        @Override
        public void appendEntries(KVStoreProto.AppendRequest req, StreamObserver<KVStoreProto.AppendResponse> responseObserver) {
            // 1. Heartbeat Logic
            if (req.getTerm() >= raft.getTerm()) {
                raft.setTerm(req.getTerm());
                raft.setState(RaftNode.State.FOLLOWER);
                raft.recordHeartbeat();
            }
            
            // 2. Replication Logic (NEW)
            if (!req.getKey().isEmpty()) {
                try {
                    int key = Integer.parseInt(req.getKey());
                    wal.writeEntry(key, req.getValue()); // Write to Disk
                    storage.insert(key, req.getValue()); // Write to RAM
                    System.out.println("Follower replicated: " + key);
                } catch (Exception e) {
                    System.err.println("Follower write failed: " + e.getMessage());
                }
            }
            
            responseObserver.onNext(KVStoreProto.AppendResponse.newBuilder()
                .setTerm(raft.getTerm())
                .setSuccess(true)
                .build());
            responseObserver.onCompleted();
        }
    }
}