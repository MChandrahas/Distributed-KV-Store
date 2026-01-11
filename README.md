# Distributed KV Store: A Learning Project

**Status:** Phase 5 (Complete: Raft Consensus & Log Replication)  
**License:** MIT  
**Code:** https://github.com/MChandrahas/Distributed-KV-Store

---

## 1. Project Overview

### Purpose
This is an **academic-grade exploration** of distributed systems fundamentals. The goal is to **build, break, and learn** to understand why systems like etcd, CockroachDB, and ZooKeeper are complex by implementing the core algorithms from scratch.

**Core Architecture:** 3-Node Cluster with Raft Consensus  

**Key Lesson:**  *Availability is easy; Consistency is hard. You cannot have a distributed database without a Write-Ahead Log and a Consensus Algorithm.*

---

## 2. System Architecture (Final Phase)

```mermaid
graph TD
    Client[Client] -->|1. PUT key=500| Leader[Leader Node]

    subgraph "Raft Cluster (gRPC/Protobuf)"
        Leader -->|2. Write WAL & RAM| LeaderStore[(Leader Storage)]
        Leader -->|3. Replicate Data| Follower1[Follower Node 1]
        Leader -->|3. Replicate Data| Follower2[Follower Node 2]

        Follower1 -->|4. Write WAL & RAM| F1Store[(Follower Storage)]
        Follower2 -->|4. Write WAL & RAM| F2Store[(Follower Storage)]
    end

    Client -.->|5. GET key=500| Follower1
````

### Components

* **Consensus:** **Raft Algorithm** (Leader Election, Randomized Timeouts, Term Numbers)
* **Replication:** **Log Replication** (Leader pushes updates to followers; strong consistency)
* **Persistence:** **Write-Ahead Log (WAL)** ensures crash recovery
* **Storage Engine:** In-memory B+ Tree (Order 128) backed by disk
* **Communication:** gRPC (Protobuf) over Netty

---
## 2b. Cloud-Native Observability (New)

To meet modern observability standards, I implemented a **Sidecar Pattern** using Go and Kubernetes.

* **Architecture:** The Java application runs in a Pod alongside a **Go-based Prometheus Exporter**.
* **Mechanism:** The Go sidecar scrapes the Java app's internal JSON state (`/status`) over localhost and converts it into OpenMetrics standard format (`/metrics`) for Prometheus.
* **Orchestration:** Deployed on **MicroK8s** using StatefulSets to maintain stable network identity for Raft consensus.

```mermaid
graph LR
    subgraph "Kubernetes Pod"
        Java[Java KV Store] -- JSON (localhost:8080) --> Go[Go Exporter]
    end
    Go -- Metrics (Port 9100) --> Prometheus[Prometheus / Grafana]
```
---

## 3. Quick Start

### Prerequisites

* Java 17+
* Docker & Docker Compose

### Run the Cluster

```bash
# 1. Start 3 nodes (Raft Cluster)
docker-compose up -d

# 2. View leader election in real time
docker-compose logs -f | grep "LEADER"
```

### Run the Client

```bash
# Write to the Leader (e.g., Port 9093)
./gradlew runClient --args="9093 PUT 100 Alice"

# Read from a Follower (e.g., Port 9091)
./gradlew runClient --args="9091 GET 100"
```

---

## 4. Experiments & Failures (The “Why”)

I used this project to verify distributed systems theory. Below is the critical failure that forced the Raft implementation.

### Experiment: The “Split-Brain” Disaster

**Phase:** Week 2 (Naive Replication) 

**Status:** **SOLVED (Phase 4)**

#### The Failure

1. Started cluster (Nodes 1, 2, 3)
2. Killed the leader (Node 1)
3. **Result:** Nodes 2 and 3 both promoted themselves to leader
4. **Impact:**

   * Node 2 accepted `Key=A`
   * Node 3 accepted `Key=B`
   * Data diverged permanently

#### The Solution (Raft)

I implemented **Raft Leader Election**. Now, when a leader dies:

1. Followers wait for a randomized timeout (e.g., 1000-2000 ms to account for Docker latency)
2. They request votes with a **term number**
3. Only one node can achieve majority (2/3)
4. **Result:** Split-brain is mathematically impossible

---
## 5. Benchmarks

**Test Setup:** Docker Desktop (WSL2) on Local Machine. Single-threaded blocking client.

| Operation          | Scenario                   | Latency (Avg) | Throughput       |
| ------------------ | -------------------------- | ------------- | ---------------- |
| **B+ Tree Insert** | In-Memory Unit Test        | **~0.004 ms** | **~250,000 op/s**|
| **Cluster Write**  | 3-Node Raft (Leader)       | ~15.0 ms*     | ~60 op/s         |
| **Cluster Read**   | 3-Node Raft (Follower)     | ~2.0 ms       | ~500 op/s        |

*\*Note: Write latency is intentionally high. It includes Synchronous Replication to 3 nodes and Disk I/O (WAL) to ensure Strong Consistency.*
---

## 6. Limitations (Interview Guide)

**Explicit gaps compared to production systems (e.g., etcd):**

* **Log Compaction:** WAL grows indefinitely; snapshotting needed
* **Dynamic Membership:** Static peer list via env vars
* **Batching:** Each `PUT` replicated individually
* **Sharding:** No horizontal partitioning (replicated state machine)

---

## 7. Tech Stack

* **Java 17** Core logic & B+ Tree Engine
* **gRPC / Protobuf** RPC layer for node communication
* **Netty** Underlying non-blocking I/O framework
* **Docker Compose** Cluster orchestration
* **Gradle 8.5** Build tool

---

## License

MIT   use this to learn from my mistakes.
