package com.distributed.kv;

import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;

public class Server {
    
    // 1. Initialize the storage engine (Order 128)
    private static final BPlusTree storage = new BPlusTree(128);

    public static void main(String[] args) throws IOException, InterruptedException {
        // 2. Start the gRPC Server
        io.grpc.Server server = ServerBuilder.forPort(9090)
                .addService(new KVServiceImpl())
                .build()
                .start();

        System.out.println("--- Distributed KV Store ---");
        System.out.println("Server started on Port 9090");
        System.out.println("Press Ctrl+C to stop...");
        
        // 3. Keep the program running
        server.awaitTermination();
    }

    // 4. The Request Handler (Now with Logging!)
    static class KVServiceImpl extends KVStoreGrpc.KVStoreImplBase {
        
        @Override
        public void put(KVStoreProto.PutRequest req, StreamObserver<KVStoreProto.PutResponse> responseObserver) {
            // LOGGING HERE
            System.out.println("[Server] RECEIVED PUT: Key=" + req.getKey() + ", Val=" + req.getValue());
            
            try {
                int key = Integer.parseInt(req.getKey());
                storage.insert(key, req.getValue());
                
                KVStoreProto.PutResponse resp = KVStoreProto.PutResponse.newBuilder().setSuccess(true).build();
                responseObserver.onNext(resp);
                responseObserver.onCompleted();
            } catch (NumberFormatException e) {
                System.err.println("[Server] ERROR: Invalid key " + req.getKey());
                responseObserver.onError(e);
            }
        }

        @Override
        public void get(KVStoreProto.GetRequest req, StreamObserver<KVStoreProto.GetResponse> responseObserver) {
            // LOGGING HERE
            System.out.println("[Server] RECEIVED GET: Key=" + req.getKey());
            
            int key = Integer.parseInt(req.getKey());
            String val = storage.search(key);
            
            KVStoreProto.GetResponse.Builder builder = KVStoreProto.GetResponse.newBuilder();
            if (val != null) {
                builder.setValue(val);
                System.out.println("[Server] FOUND: " + val);
            } else {
                System.out.println("[Server] NOT FOUND");
            }
            
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}