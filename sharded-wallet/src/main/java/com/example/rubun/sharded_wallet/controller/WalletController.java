package com.example.rubun.sharded_wallet.controller;

import com.example.rubun.sharded_wallet.dto.*;
import com.example.rubun.sharded_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.rubun.sharded_wallet.entity.TransactionHistory;
import com.example.rubun.sharded_wallet.repository.TransactionHistoryRepository;
import java.util.List;


@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final TransactionHistoryRepository historyRepository;

    // Get your own wallet balance
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getBalance(
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        WalletResponse response = walletService.getWalletByUserId(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Wallet fetched", response));
    }

    // Look up any wallet by UPI ID — used when sending money
    @GetMapping("/lookup/{upiId}")
    public ResponseEntity<ApiResponse<WalletResponse>> lookupByUpiId(
            @PathVariable String upiId) {
        WalletResponse response = walletService.getWalletByUpiId(upiId);
        return ResponseEntity.ok(
                ApiResponse.success("Wallet found", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TransactionHistory>>> getHistory(
            Authentication authentication) {
        Long userId = (Long) authentication.getCredentials();
        WalletResponse wallet = walletService.getWalletByUserId(userId);
        List<TransactionHistory> history =
                historyRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());
        return ResponseEntity.ok(
                ApiResponse.success("History fetched", history));
    }
}