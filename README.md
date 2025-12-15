
# Distributed KV Store: A Learning Project

**Status:** Week 2 (Leader–Follower Replication)  
**License:** MIT  
**Code:** https://github.com/MChandrahas/Distributed-KV-Store

---

## 1. Project Overview

### Purpose
This is an **academic-grade exploration** of distributed systems fundamentals. The goal is to **build, break, and learn**—to understand why systems like etcd and ZooKeeper are complex by experiencing the pain of data inconsistency firsthand.

**Current Scope:** Leader–Follower replication with asynchronous writes  
**Core Lesson:** Without a consensus algorithm (Raft/Paxos), a distributed system is just a *“Split-Brain Generator.”*

---

## 2. System Architecture (Phase 2)

```mermaid
graph TD
    Client[gRPC Client] -->|PUT key=100| Leader[Node 1: Leader]
    Leader -->|Async Replicate| Follower1[Node 2: Follower]
    Leader -->|Async Replicate| Follower2[Node 3: Follower]

    subgraph "Internal Node Architecture"
        Leader
        Index[(B+ Tree)]
    end
````

### Components

* **Communication:** gRPC (Protobuf) over Netty
* **Storage Engine:** In-memory B+ Tree (Order 128)
* **Replication:** Naive Leader–Follower (Push model)
* **Discovery:** Static peer lists via environment variables

---

## 3. Quick Start

### Prerequisites

* Java 17+
* Docker & Docker Compose

### Run the Cluster

```bash
# 1. Start 3 nodes (Leader + 2 Followers)
docker-compose up -d

# 2. Check status
docker ps
```

### Run the Client

```bash
# Write to Leader (Port 9091)
./gradlew runClient --args="9091 PUT 100 Alice"

# Read from Follower (Port 9092)
./gradlew runClient --args="9092 GET 100"
```

---

## 4. Experiments & Failures (The “Why”)

I use this project to verify distributed systems theory. Below is the latest confirmed failure.

### Experiment: The “Ghost Data” Failure (Consistency Violation)

**Date:** Week 2
**Status:** CONFIRMED ✅

#### The Test

1. Start cluster (Nodes 1, 2, 3).
2. **Kill Node 2** (`docker stop kv-node-2`).
3. Write `Key=999` to Leader. Leader accepts it (Availability > Consistency).
4. **Revive Node 2** (`docker start kv-node-2`).
5. Read `Key=999` from Node 2.

#### The Result

* Leader returns: `Value: GhostData`
* Node 2 returns: `null` (data missing)

#### Root Cause

The system lacks **Anti-Entropy** (log replay). When a node reconnects, it has no way to ask *“What did I miss?”*. This proves naive replication is insufficient for data safety.

**Next Step (Phase 3/4):** Implement Raft log matching to fix this.

---

## 5. Benchmarks

| Operation            | Target   | Latency (Avg) | Throughput   |
| -------------------- | -------- | ------------- | ------------ |
| **Local Write**      | B+ Tree  | 0.05 ms       | ~20k ops/sec |
| **Replicated Write** | 3 Nodes  | 2.10 ms       | ~4k ops/sec  |
| **Stale Read**       | Follower | 0.80 ms       | High         |

---

## 6. Limitations (Interview Guide)

**Explicit gaps between this and production:**

* **No Persistence:** Data lives in RAM. A crash = total data loss.
* **No Leader Election:** If Node 1 dies, the system is dead.
* **No WAL:** Cannot replay transactions on startup.
* **No Sharding:** Every node holds 100% of the data.

---

## 7. Tech Stack

* **Java 17**
* **gRPC / Protobuf**
* **Docker Compose**
* **Gradle 8.5**

---

## License

MIT — use this to learn from my mistakes.