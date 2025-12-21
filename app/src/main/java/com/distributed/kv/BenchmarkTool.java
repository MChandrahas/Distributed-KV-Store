package com.distributed.kv;

import java.util.UUID;

public class BenchmarkTool {

    public static void main(String[] args) {
        System.out.println(">>> STARTING BENCHMARK TOOL <<<");
        
        // 1. Initialize Tree with Order 6
        // Note: Using order 6 makes it deeper/more complex, good for stress testing.
        BPlusTree tree = new BPlusTree(6); 

        int operations = 100_000; // 100k operations
        System.out.println("Running " + operations + " insertions...");

        long startTime = System.currentTimeMillis();

        // 2. The Loop
        for (int i = 0; i < operations; i++) {
            tree.insert(i, "val-" + UUID.randomUUID().toString());
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 3. Calculate Speed
        if (duration == 0) duration = 1; 
        double opsPerSec = (operations / (double) duration) * 1000;

        System.out.println("=========================================");
        System.out.println("  B+ TREE WRITE SPEED: " + (int)opsPerSec + " OPS/SEC");
        System.out.println("=========================================");
    }
}