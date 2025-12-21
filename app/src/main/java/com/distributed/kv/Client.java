package com.distributed.kv;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.UUID;

public class Client {
    public static void main(String[] args) {
        // Default to Node 3 (Leader usually on 9093) if no args
        int port = 9093;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        System.out.println("--- Connecting to Node at Port " + port + " ---");

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();

        KVStoreGrpc.KVStoreBlockingStub stub = KVStoreGrpc.newBlockingStub(channel);

        if (args.length > 1 && args[1].equals("PUT")) {
            // Standard Single Put
            System.out.println("PUT key=" + args[2] + " value=" + args[3]);
            stub.put(KVStoreProto.PutRequest.newBuilder().setKey(args[2]).setValue(args[3]).build());
            System.out.println("Success!");

        } else if (args.length > 1 && args[1].equals("GET")) {
            // Standard Single Get
            System.out.println("GET key=" + args[2]);
            KVStoreProto.GetResponse resp = stub.get(KVStoreProto.GetRequest.newBuilder().setKey(args[2]).build());
            System.out.println("Value: " + resp.getValue());

        } else if (args.length > 1 && args[1].equals("BENCHMARK")) {
            // --- NEW BENCHMARK MODE ---
            int operations = 100; // Run 100 requests (safe number for synchronous Raft)
            System.out.println(">>> Starting Write Benchmark (" + operations + " reqs) <<<");

            long start = System.currentTimeMillis();

            for (int i = 0; i < operations; i++) {
                String key = "bench-" + i;
                String val = UUID.randomUUID().toString();
                try {
                    stub.put(KVStoreProto.PutRequest.newBuilder().setKey(key).setValue(val).build());
                    if (i % 10 == 0) System.out.print("."); // Progress bar
                } catch (Exception e) {
                    System.err.println("Request failed: " + e.getMessage());
                }
            }
            
            long end = System.currentTimeMillis();
            long duration = end - start;
            if (duration == 0) duration = 1;

            System.out.println("\n\n=== RESULTS ===");
            System.out.println("Total Time: " + duration + " ms");
            System.out.println("Throughput: " + (operations * 1000 / duration) + " ops/sec");
            System.out.println("Avg Latency: " + (duration / (double)operations) + " ms/req");
            System.out.println("===============");
        }

        channel.shutdown();
    }
}