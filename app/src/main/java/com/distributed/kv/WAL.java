package com.distributed.kv;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class WAL {
    private final File file;
    private final DataOutputStream out;

    public WAL(String filename) throws IOException {
        this.file = new File(filename);
        
        // "true" means append mode (don't overwrite existing data)
        this.out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(file, true))
        );
    }

    /**
     * Format on Disk:
     * [4 bytes: Key (Int)] [4 bytes: Val Length] [N bytes: Value]
     */
    public synchronized void writeEntry(int key, String value) throws IOException {
        byte[] valBytes = value.getBytes(StandardCharsets.UTF_8);

        out.writeInt(key);               // Write Key
        out.writeInt(valBytes.length);   // Write Value Size
        out.write(valBytes);             // Write Value Data
        
        out.flush(); // Force data from RAM buffer to OS buffer
        // In production, we would also call fd.sync() here for strong durability
    }

    /**
     * Reads the entire log from disk to rebuild the B+ Tree
     */
    public void replay(BPlusTree tree) throws IOException {
        if (!file.exists()) return;

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            
            while (in.available() > 0) {
                int key = in.readInt();
                int len = in.readInt();
                
                byte[] valBytes = new byte[len];
                in.readFully(valBytes);
                String value = new String(valBytes, StandardCharsets.UTF_8);

                // Replay into memory
                tree.insert(key, value);
            }
        }
        System.out.println("[WAL] Replayed data from disk successfully.");
    }
}