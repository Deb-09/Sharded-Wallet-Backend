package com.example.rubun.sharded_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_history")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TransactionHistory {

    @Id
    @Column(name = "txn_id")
    private Long txnId;

    // Sharding key
    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 10)
    private TxnType txnType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Snapshot of balance before and after — lets you audit any point in time
    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum TxnType {
        DEBIT, CREDIT, ROLLBACK
    }
}