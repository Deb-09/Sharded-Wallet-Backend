package com.example.rubun.sharded_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_outbox")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TransactionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // Sharding key — outbox rows live on the same shard as the wallet
    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    // e.g. DEBIT_INITIATED, CREDIT_INITIATED, ROLLBACK_INITIATED
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    // JSON string of the full event payload
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = OutboxStatus.PENDING;
    }

    public enum OutboxStatus {
        PENDING, PUBLISHED, FAILED
    }
}