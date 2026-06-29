package com.example.rubun.sharded_wallet.service;

import com.example.rubun.sharded_wallet.dto.WalletResponse;
import com.example.rubun.sharded_wallet.entity.Wallet;
import com.example.rubun.sharded_wallet.repository.TransactionHistoryRepository;
import com.example.rubun.sharded_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository            walletRepository;
    private final TransactionHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWalletByUpiId(String upiId) {
        Wallet wallet = walletRepository.findByUpiId(upiId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for UPI ID: " + upiId));
        return toResponse(wallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getWalletId())
                .upiId(wallet.getUpiId())
                .balance(wallet.getBalance())
                .status(wallet.getStatus().name())
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}