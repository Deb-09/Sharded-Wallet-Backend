# Sharded-Wallet-Backend

# Distributed Horizontally Scaled Wallet System (Backend)

A high-throughput, horizontally scaled distributed wallet system built with **Spring Boot 3** and **PostgreSQL**. The system utilizes **Saga Orchestration** for cross-shard eventual consistency, row-level **Pessimistic Locking** to guarantee race-condition safety, the **Transactional Outbox Pattern** to decouple database state from messaging infrastructure, and custom **Snowflake IDs** for deterministic data sharding.

---

## 🏗️ Architecture Overview

The core challenge of this system is executing transactional movements of money across distinct data partitions (shards) without locking entire database instances or introducing distributed deadlocks.

Transfer Request (Idempotency Key)
│
▼
┌───────────────────┐
│ Saga Orchestrator │ ───► Persists state to saga_state table
└───────────────────┘
│
┌───────┴───────┐
▼               ▼
┌──────────────┐┌──────────────┐
│ executeDebit ││─►executeCredit│
└──────────────┘└──────────────┘
(Shard Alloc)   (Shard Alloc)
│               │
▼               ▼
┌───────────┐   ┌───────────┐
│  Shard 0  │   │  Shard 1  │  (Routed deterministically via wallet_id % 3)
└───────────┘   └───────────┘


### 🛰️ The Layered Safety Stack

| Layer | Mechanism | What It Prevents |
| :--- | :--- | :--- |
| **API** | **Idempotency Key** (Stored UUID) | Duplicate processing from network retries / double-clicks |
| **DB** | **Pessimistic Locking** (`SELECT FOR UPDATE`) | Concurrent balance reads/writes & Time-of-Check to Time-of-Use (TOCTOU) bugs |
| **DB** | **Check Constraint** (`balance >= 0`) | Database-level final defense against accidental negative balances |
| **Saga** | **Persistent State Machine** | Partial failures or network drops leading to corrupted distributed states |
| **Kafka** | **Transactional Outbox Pattern** | Dual-write failures (ensuring message publication if and only if DB transaction commits) |

---

## 🛠️ Key Technical Implementations

### 1. Manual Sharding with `AbstractRoutingDataSource`
Instead of relying on heavy third-party proxy dependencies, data routing is achieved cleanly via Spring’s built-in routing mechanism coupled with a `ThreadLocal` context. Wallets are split across logical database instances based on a deterministic sharding formula:
$$\text{Shard Index} = \text{wallet\_id} \pmod 3$$

### 2. Time-Sortable Snowflake IDs
Standard auto-increment sequences fail in a sharded environment due to collisions. We implement a custom 64-bit thread-safe Snowflake ID generator:
* **41 bits:** Custom Epoch timestamp resolution (gives ~69 years of sorting space).
* **10 bits:** Datacenter and Machine coordinates (enabling horizontal instance expansion without coordination).
* **12 bits:** Sequential counter (allowing up to 4,096 distinct IDs per millisecond per machine).

### 3. The Transactional Outbox Pattern
To safely emit event notifications to Apache Kafka without introducing the fragile "dual-write problem", events (such as `DEBIT_COMPLETED`) are recorded to a `transaction_outbox` table within the **same atomic database transaction** as the financial balance state modification. A background `@Scheduled` thread utilizes `SKIP LOCKED` mechanics to safely stream pending records out to Kafka sequentially.

---

## 💻 Tech Stack
* **Java 21** & **Spring Boot 3.x**
* **Spring Data JPA** & **Hibernate**
* **PostgreSQL** (Multi-shard cluster routing layout)
* **Flyway** (Version-controlled schema migrations)
* **Spring Security** & **JJWT** (Stateless Bearer token filtering)
* **Docker** & **Apache Kafka** (Asynchronous event dissemination foundation)

---