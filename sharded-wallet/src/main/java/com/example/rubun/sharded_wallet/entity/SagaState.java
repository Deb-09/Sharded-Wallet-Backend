package com.example.rubun.sharded_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_state")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SagaState {

    @Id
    @Column(name = "saga_id", length = 36)
    private String sagaId;

    // Idempotency key sent by the client — if same key comes twice
    // we return the existing saga result instead of processing again
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "sender_wallet_id", nullable = false)
    private Long senderWalletId;

    @Column(name = "receiver_wallet_id", nullable = false)
    private Long receiverWalletId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 30)
    private SagaStep currentStep;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null)      this.status = SagaStatus.INITIATED;
        if (this.currentStep == null) this.currentStep = SagaStep.DEBIT;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum SagaStatus {
        INITIATED,
        DEBITED,
        CREDITED,
        COMPLETED,
        DEBIT_FAILED,
        CREDIT_FAILED,
        ROLLBACK_INITIATED,
        ROLLBACK_COMPLETE
    }

    public enum SagaStep {
        DEBIT,
        CREDIT,
        CONFIRM,
        ROLLBACK
    }
}