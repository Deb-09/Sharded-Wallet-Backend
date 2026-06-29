package com.example.rubun.sharded_wallet.repository;

import com.example.rubun.sharded_wallet.entity.TransactionOutbox;
import com.example.rubun.sharded_wallet.entity.TransactionOutbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionOutboxRepository
        extends JpaRepository<TransactionOutbox, Long> {

    // SKIP LOCKED — if another scheduler thread already picked up a row,
    // skip it instead of waiting. Prevents duplicate Kafka publishes
    // when multiple instances of the app are running
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT t FROM TransactionOutbox t " +
            "WHERE t.status = 'PENDING' " +
            "ORDER BY t.createdAt ASC")
    List<TransactionOutbox> findPendingOutboxEvents();
}