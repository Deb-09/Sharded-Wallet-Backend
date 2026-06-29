package com.example.rubun.sharded_wallet.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "upi_id", nullable = false, unique = true, length = 50)
    private String upiId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // Optimistic locking — Hibernate auto increments this on every update
    // If two transactions read version=5 and both try to write,
    // the second one gets an OptimisticLockException and must retry
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) this.status = WalletStatus.ACTIVE;
        if (this.balance == null) this.balance = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum WalletStatus {
        ACTIVE, FROZEN, CLOSED
    }
}