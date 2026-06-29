package com.example.rubun.sharded_wallet.repository;

import com.example.rubun.sharded_wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUpiId(String upiId);

    Optional<Wallet> findByUserId(Long userId);

    // Pessimistic write lock — translates to SELECT ... FOR UPDATE in SQL
    // No other transaction can read or write this row until we commit
    // This is our primary defense against race conditions and double spend
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.walletId = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") Long walletId);

    // Same but by UPI ID — used when sender types recipient UPI ID
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.upiId = :upiId")
    Optional<Wallet> findByUpiIdWithLock(@Param("upiId") String upiId);
}