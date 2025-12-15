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
    // List of other nodes we need to replicate to
    private static final List<KVStoreGrpc.KVStoreBlockingStub> followers = new ArrayList<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Check for Peers (Replication targets)
        String peersEnv = System.getenv("PEERS");
        if (peersEnv != null && !peersEnv.isEmpty()) {
            String[] addresses = peersEnv.split(",");
            for (String addr : addresses) {
                String[] parts = addr.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);
                
                // Create a connection to the follower
                ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                        .usePlaintext()
                        .build();
                followers.add(KVStoreGrpc.newBlockingStub(channel));
                System.out.println("[Leader] Replication enabled to: " + host);
            }
        }

        // 2. Start Server
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
            System.out.println("[Server] WRITE: Key=" + req.getKey() + ", Val=" + req.getValue());
            
            try {
                // 1. Write Locally
                int key = Integer.parseInt(req.getKey());
                storage.insert(key, req.getValue());

                // 2. Replicate to Followers (if any)
                // This is synchronous replication (Leader waits for Followers)
                for (KVStoreGrpc.KVStoreBlockingStub follower : followers) {
                    try {
                        follower.put(req); 
                        System.out.println("   -> Replicated to follower");
                    } catch (Exception e) {
                        System.err.println("   -> Replication Failed: " + e.getMessage());
                    }
                }
                
                KVStoreProto.PutResponse resp = KVStoreProto.PutResponse.newBuilder().setSuccess(true).build();
                responseObserver.onNext(resp);
                responseObserver.onCompleted();

            } catch (Exception e) {
                responseObserver.onError(e);
            }
        }

        @Override
        public void get(KVStoreProto.GetRequest req, StreamObserver<KVStoreProto.GetResponse> responseObserver) {
            // Reads are local only (Eventual Consistency)
            int key = Integer.parseInt(req.getKey());
            String val = storage.search(key);
            
            KVStoreProto.GetResponse.Builder builder = KVStoreProto.GetResponse.newBuilder();
            if (val != null) builder.setValue(val);
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}