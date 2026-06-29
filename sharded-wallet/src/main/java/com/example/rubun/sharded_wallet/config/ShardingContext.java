package com.example.rubun.sharded_wallet.config;

public class ShardingContext {

    private static final ThreadLocal<String> CURRENT_SHARD =
            new ThreadLocal<>();

    public static void setShard(Long walletId) {
        int shardIndex = (int) (walletId % 3);
        CURRENT_SHARD.set("shard" + shardIndex);
    }

    public static void setShard(String shardKey) {
        CURRENT_SHARD.set(shardKey);
    }

    public static String getCurrentShard() {
        String shard = CURRENT_SHARD.get();
        return shard != null ? shard : "shard0";
    }

    public static void clear() {
        CURRENT_SHARD.remove();
    }
}