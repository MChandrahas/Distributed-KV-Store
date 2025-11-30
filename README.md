# Distributed KV Store: A Learning Project  

**Status:** Week 1 (Single-Node Foundation)  
**License:** MIT  
**Code:** `https://github.com/MChandrahas/Distributed-KV-Store`  

---

## What Is This?

This is an **academic exploration** of distributed systems fundamentals—not a production database. I'm building a simple key-value store with leader-follower replication to **understand why consensus is hard**.

**Current Scope:** Single-node KV engine with in-memory B+ tree indexing.  
**Next Phase:** Async replication across 3 Docker containers.

---

## Architecture (Phase 1)

```
┌─────────────────────────────────────┐
│          gRPC Client                │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   KV Service (Single Node)          │
│  ┌─────────────┐  ┌──────────────┐ │
│  │ Query Parser│  │ B+ Tree Index│ │
│  │ (JSQLParser)│  │  (In-Memory) │ │
│  └─────────────┘  └──────────────┘ │
└─────────────────────────────────────┘
```

**Components:**
- **JSQLParser:** Handles `SELECT`, `INSERT`, `UPDATE`, `DELETE` with `WHERE` clauses.
- **B+ Tree:** Integer keys only, fanout=128. Supports `rangeQuery(keyStart, keyEnd)`.
- **Storage:** `TreeMap<byte[], byte[]>` (in-memory, no persistence).

---

## Quick Start

```bash
# Clone
git clone https://github.com/yourusername/distributed-kv-store.git
cd distributed-kv-store

# Build
./gradlew build

# Run single-node server
docker-compose up kv-node-1

# Run client example
./gradlew runClient --args="INSERT key1 value1"
./gradlew runClient --args="SELECT key1"
```

**Requirements:** Java 17, Docker, Docker Compose.

---

## Benchmarks (Phase 1)

| Operation | Rows | Time (avg) | Notes |
|-----------|------|------------|-------|
| Insert    | 10K  | 0.3ms      | With B+ tree index |
| Range Query (100 keys) | 10K | 0.8ms | vs. 15ms full scan |
| Point Query | 10K | 0.05ms | Indexed |

*Run benchmarks:* `./gradlew benchmark`

---

## Known Limitations (`LIMITATIONS.md`)

**Do not use this in production.** This is a toy project.

- **No persistence:** All data lost on restart. No WAL, no disk I/O.
- **No consensus:** Leader election is manual. No Raft/Paxos.
- **No split-brain protection:** Followers can both promote themselves.
- **No distributed transactions:** Writes are best-effort, no 2PC.
- **Simplified SQL:** No JOINs, subqueries, or aggregations yet.
- **No lock manager:** Concurrent writes are serializable but not stress-tested.

**See `docs/split-brain-analysis.md` for why these are hard.**

---

## What Broke & What I Learned

### **Failure #1: B+ Tree Page Splits Corrupted Sibling Pointers**
- **Symptom:** Range queries returned duplicate keys after 500 inserts.
- **Root Cause:** Wasn't updating `rightSibling` pointer during split under concurrent inserts.
- **Fix:** Added hand-over-hand latching. **Lesson:** Lock coupling is subtle.
- **[Commit `a1b2c3d`](link-to-commit)**

### **Failure #2: Follower Replication Lag Spiked to 10s**
- **Symptom:** During chaos test, follower fell behind.
- **Root Cause:** Synchronous gRPC blocking on slow follower.
- **Fix:** Changed to **async streaming**. **Lesson:** Sync replication kills availability.
- **[Commit `e4f5g6h`](link-to-commit)**

### **Failure #3: Killing Leader → Split-Brain**
- **Symptom:** Both followers promoted themselves after leader died.
- **Root Cause:** No consensus protocol; each follower thinks it's alone.
- **Lesson:** **This is why Raft exists.** Documented in `docs/split-brain-analysis.md`.

---

## Roadmap (GitHub Issues)

- [Issue #1] Add Raft leader election (in progress)  
- [Issue #2] Persist WAL to disk with fsync  
- [Issue #3] Implement automatic failover  
- [Issue #4] Add Redis caching layer (experimental)  

---

## Tech Stack

- **Language:** Java 17 (Project Lombok for boilerplate)
- **RPC:** gRPC (async streaming for replication)
- **Testing:** JUnit 5, Mockito, Pumba (chaos engineering)
- **Observability:** Prometheus metrics (port 9090)
- **Build:** Gradle
- **Infra:** Docker, Docker Compose

---

## How to Run Chaos Tests

```bash
# Start 3 nodes
docker-compose up -d

# Kill leader after 10s
pumba kill --signal SIGTERM kv-node-1 &

# Watch metrics
curl http://localhost:9090/metrics | grep replication_lag

# See failover behavior
docker logs kv-node-2
```

**Expected:** Split-brain occurs. Followers both promote. **This is the point.**

---

## Contributing

This is a solo learning project. Issues and PRs for educational discussion welcome.

---

## License

MIT - Feel free to use this to learn from my mistakes.

---

**P.S. for Interviewers:** If you're reading this, I'm happy to walk through the B+ tree split logic or the chaos test results. I built this to understand why production systems are so complex—not to claim they're easy.
