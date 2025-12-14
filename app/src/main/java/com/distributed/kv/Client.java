package com.distributed.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {
    public static void main(String[] args) {
        // 1. Create a connection to the Server
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext() // Disable SSL for local testing
                .build();

        // 2. Create the "Stub" (The object we call methods on)
        KVStoreGrpc.KVStoreBlockingStub stub = KVStoreGrpc.newBlockingStub(channel);

        System.out.println("--- Client Started ---");

        // --- TEST 1: PUT ---
        System.out.println("1. Inserting Key=1001, Value=Alice");
        KVStoreProto.PutRequest putReq = KVStoreProto.PutRequest.newBuilder()
                .setKey("1001")
                .setValue("Alice")
                .build();
        
        KVStoreProto.PutResponse putResp = stub.put(putReq);
        System.out.println("   Success: " + putResp.getSuccess());

        // --- TEST 2: GET ---
        System.out.println("2. Querying Key=1001");
        KVStoreProto.GetRequest getReq = KVStoreProto.GetRequest.newBuilder()
                .setKey("1001")
                .build();
        
        KVStoreProto.GetResponse getResp = stub.get(getReq);
        System.out.println("   Result: " + getResp.getValue());

        channel.shutdown();
    }
}