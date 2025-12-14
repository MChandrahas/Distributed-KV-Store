package com.distributed.kv;

import java.util.*;

public class BPlusTree {
    private int order;
    private Node root;

    public BPlusTree(int order) {
        this.order = order;
        this.root = new Node(true); // Start with a leaf root
    }

    // --- PUBLIC API ---

    public String search(int key) {
        Node leaf = findLeaf(key);
        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx >= 0) {
            return leaf.values.get(idx);
        }
        return null;
    }

    public void insert(int key, String value) {
        Node leaf = findLeaf(key);
        int idx = Collections.binarySearch(leaf.keys, key);

        if (idx >= 0) {
            // Key exists -> Update value
            leaf.values.set(idx, value);
        } else {
            // Key is new -> Insert sorted
            int pos = -(idx + 1);
            leaf.keys.add(pos, key);
            leaf.values.add(pos, value);
        }
    }

    // --- INTERNAL HELPERS ---

    private Node findLeaf(int key) {
        Node curr = root;
        while (!curr.isLeaf) {
            int i = 0;
            // Find the first key strictly greater than ours
            while (i < curr.keys.size() && key >= curr.keys.get(i)) {
                i++;
            }
            curr = curr.children.get(i);
        }
        return curr;
    }

    // --- NODE STRUCTURE ---
    private static class Node {
        boolean isLeaf;
        List<Integer> keys = new ArrayList<>();
        List<String> values = new ArrayList<>(); // Only for leaf nodes
        List<Node> children = new ArrayList<>(); // Only for internal nodes

        Node(boolean isLeaf) { 
            this.isLeaf = isLeaf; 
        }
    }
}