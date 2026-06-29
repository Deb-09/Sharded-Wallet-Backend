package com.example.rubun.sharded_wallet.repository;

import com.example.rubun.sharded_wallet.entity.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionHistoryRepository
        extends JpaRepository<TransactionHistory, Long> {

    // Full transaction history for a wallet ordered newest first
    List<TransactionHistory> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<TransactionHistory> findBySagaId(String sagaId);
}