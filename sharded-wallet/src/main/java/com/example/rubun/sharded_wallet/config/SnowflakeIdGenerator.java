package com.example.rubun.sharded_wallet.config;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    // Epoch: Jan 1 2024 00:00:00 UTC — all IDs are relative to this
    private static final long EPOCH = 1704067200000L;

    // Bit allocation: 41 bits timestamp | 5 bits datacenterId | 5 bits machineId | 12 bits sequence
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MACHINE_ID_BITS    = 5L;
    private static final long SEQUENCE_BITS      = 12L;

    private static final long MAX_DATACENTER_ID  = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long MAX_MACHINE_ID     = ~(-1L << MACHINE_ID_BITS);    // 31
    private static final long MAX_SEQUENCE       = ~(-1L << SEQUENCE_BITS);      // 4095

    private static final long MACHINE_ID_SHIFT      = SEQUENCE_BITS;                             // 12
    private static final long DATACENTER_ID_SHIFT   = SEQUENCE_BITS + MACHINE_ID_BITS;           // 17
    private static final long TIMESTAMP_SHIFT       = SEQUENCE_BITS + MACHINE_ID_BITS
            + DATACENTER_ID_BITS;                       // 22

    private final long datacenterId;
    private final long machineId;

    private long sequence        = 0L;
    private long lastTimestamp   = -1L;

    // For local dev: datacenterId=1, machineId=1
    // In production these would come from application.yml per deployed instance
    public SnowflakeIdGenerator() {
        this.datacenterId = 1L;
        this.machineId    = 1L;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        // Clock moved backwards — this should never happen but we guard against it
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException(
                    "Clock moved backwards. Refusing to generate ID for "
                            + (lastTimestamp - currentTimestamp) + "ms"
            );
        }

        if (currentTimestamp == lastTimestamp) {
            // Same millisecond — increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted for this ms — wait for next ms
                currentTimestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond — reset sequence
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // Assemble the 64-bit ID
        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId             << DATACENTER_ID_SHIFT)
                | (machineId               << MACHINE_ID_SHIFT)
                | sequence;
    }

    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}


//A Snowflake ID is a 64-bit long assembled from 4 parts:
// | 41 bits timestamp | 5 bits datacenter | 5 bits machine | 12 bits sequence |
//41 bits timestamp — milliseconds since our custom epoch (Jan 1 2024).
// Gives us ~69 years of IDs before overflow.
//5 bits datacenter + 5 bits machine — in production, each deployed instance gets a unique datacenterId + machineId combination.
// This is what makes IDs globally unique across machines without any central coordinator.
//12 bits sequence — allows 4096 unique IDs per millisecond per machine.
// At 3 machines that's ~12,000 IDs/ms which is more than enough.
//Why this matters for sharding — because wallet_id % 3 needs to be deterministic and globally unique.
// Auto-increment fails here because 3 separate Postgres instances would all start from 1 and collide.
// Snowflake IDs are unique across all shards with zero coordination.
//synchronized — the method is thread-safe.
// Multiple request threads hitting the same machine will queue up on this method and
// each get a unique ID without race conditions on the sequence counter.