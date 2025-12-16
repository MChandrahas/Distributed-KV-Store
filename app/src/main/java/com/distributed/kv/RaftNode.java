package com.distributed.kv;

public class RaftNode {
    public enum State {
        FOLLOWER,
        CANDIDATE,
        LEADER
    }

    private State state = State.FOLLOWER;
    private int currentTerm = 0;
    private String votedFor = null;
    
    // Who am I?
    private final String nodeId;
    
    // Time tracking (for timeouts)
    private long lastHeartbeatTime;

    public RaftNode(String nodeId) {
        this.nodeId = nodeId;
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public synchronized State getState() { return state; }
    public synchronized void setState(State s) { 
        this.state = s; 
        System.out.println("[Raft] State changed to: " + s);
    }

    public synchronized int getTerm() { return currentTerm; }
    public synchronized void setTerm(int t) { 
        if (t > this.currentTerm) {
            this.currentTerm = t;
            this.votedFor = null; // New term, new vote
            this.state = State.FOLLOWER; // Step down if we see a higher term
        }
    }

    public synchronized void recordHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public synchronized long getLastHeartbeat() {
        return lastHeartbeatTime;
    }

    public synchronized boolean voteFor(String candidateId, int term) {
        if (term > currentTerm) {
            setTerm(term);
        }
        
        if (term == currentTerm && (votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            recordHeartbeat(); // Granting a vote resets the timer
            return true;
        }
        return false;
    }
}