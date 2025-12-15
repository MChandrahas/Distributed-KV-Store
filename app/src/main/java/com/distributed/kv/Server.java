package com.distributed.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Server {
    
    private static final BPlusTree storage = new BPlusTree(128);
    private static final List<KVStoreGrpc.KVStoreBlockingStub> followers = new ArrayList<>();
    private static WAL wal;

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Initialize WAL
        // We use a specific folder "/data" which we will mount in Docker later
        String nodeId = System.getenv("HOSTNAME"); // e.g., kv-node-1
        // Use a local folder named 'kv-data' inside the project
        String dataDir = "kv-data";
        new java.io.File(dataDir).mkdirs();

        String logFile = dataDir + "/" + (nodeId != null ? nodeId : "local") + ".log";
        
        wal = new WAL(logFile);
        
        // 2. REPLAY HISTORY (Fixes Amnesia)
        System.out.println("Recovering from WAL: " + logFile);
        wal.replay(storage);

        // 3. Setup Replication Peers
        String peersEnv = System.getenv("PEERS");
        if (peersEnv != null && !peersEnv.isEmpty()) {
            String[] addresses = peersEnv.split(",");
            for (String addr : addresses) {
                String[] parts = addr.split(":");
                ManagedChannel channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                        .usePlaintext().build();
                followers.add(KVStoreGrpc.newBlockingStub(channel));
            }
        }

        // 4. Start Server
        io.grpc.Server server = ServerBuilder.forPort(9090)
                .addService(new KVServiceImpl())
                .build()
                .start();

        System.out.println("Server started on Port 9090");
        server.awaitTermination();
    }

    static class KVServiceImpl extends KVStoreGrpc.KVStoreImplBase {
        
        @Override
        public void put(KVStoreProto.PutRequest req, StreamObserver<KVStoreProto.PutResponse> responseObserver) {
            try {
                int key = Integer.parseInt(req.getKey());

                // STEP A: WRITE TO DISK FIRST (Durability)
                wal.writeEntry(key, req.getValue());

                // STEP B: WRITE TO MEMORY
                storage.insert(key, req.getValue());

                System.out.println("[Server] Persisted & Applied: " + key);

                // STEP C: REPLICATE
                for (KVStoreGrpc.KVStoreBlockingStub follower : followers) {
                    try { follower.put(req); } catch (Exception e) {}
                }
                
                responseObserver.onNext(KVStoreProto.PutResponse.newBuilder().setSuccess(true).build());
                responseObserver.onCompleted();

            } catch (Exception e) {
                System.err.println("Write failed: " + e.getMessage());
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
    }
}