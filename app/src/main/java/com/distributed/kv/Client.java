package com.distributed.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class Client {
    public static void main(String[] args) {
        // Default to Node 1 (9091) if no arguments provided
        int port = 9091;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        System.out.println("--- Connecting to Node at Port " + port + " ---");

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();

        KVStoreGrpc.KVStoreBlockingStub stub = KVStoreGrpc.newBlockingStub(channel);

        // 1. Write Data
        if (args.length > 1 && args[1].equals("PUT")) {
            System.out.println("PUT key=" + args[2] + " value=" + args[3]);
            stub.put(KVStoreProto.PutRequest.newBuilder().setKey(args[2]).setValue(args[3]).build());
            System.out.println("Success!");
        } 
        // 2. Read Data
        else if (args.length > 1 && args[1].equals("GET")) {
            System.out.println("GET key=" + args[2]);
            KVStoreProto.GetResponse resp = stub.get(KVStoreProto.GetRequest.newBuilder().setKey(args[2]).build());
            System.out.println("Value: " + resp.getValue());
        }

        channel.shutdown();
    }
}