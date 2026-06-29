package com.example.rubun.sharded_wallet.service;

import com.example.rubun.sharded_wallet.config.SnowflakeIdGenerator;
import com.example.rubun.sharded_wallet.dto.TransferRequest;
import com.example.rubun.sharded_wallet.dto.TransferResponse;
import com.example.rubun.sharded_wallet.entity.*;
import com.example.rubun.sharded_wallet.entity.SagaState.*;
import com.example.rubun.sharded_wallet.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaStateRepository         sagaStateRepository;
    private final WalletRepository            walletRepository;
    private final TransactionHistoryRepository historyRepository;
    private final TransactionOutboxRepository  outboxRepository;
    private final SnowflakeIdGenerator         snowflakeIdGenerator;
    private final ObjectMapper                 objectMapper;

    // ================================================================
    // ENTRY POINT — called by the controller
    // ================================================================
    @Transactional
    public TransferResponse initiateSaga(TransferRequest request) {

        // Step 1 — Idempotency check
        // If we've seen this key before, return the existing saga result
        // This handles duplicate HTTP requests (retries, double clicks)
        Optional<SagaState> existing =
                sagaStateRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate request detected for idempotency key: {}",
                    request.getIdempotencyKey());
            return toResponse(existing.get(), request.getSenderUpiId(),
                    request.getReceiverUpiId());
        }

        // Step 2 — Validate amount
        if (request.getAmount() == null ||
                request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Transfer amount must be greater than zero");
        }

        // Step 3 — Resolve wallets (no lock yet — just checking they exist)
        Wallet sender = walletRepository.findByUpiId(request.getSenderUpiId())
                .orElseThrow(() -> new RuntimeException(
                        "Sender wallet not found: " + request.getSenderUpiId()));
        Wallet receiver = walletRepository.findByUpiId(request.getReceiverUpiId())
                .orElseThrow(() -> new RuntimeException(
                        "Receiver wallet not found: " + request.getReceiverUpiId()));

        // Step 4 — Create saga record (INITIATED state)
        String sagaId = UUID.randomUUID().toString();
        SagaState saga = SagaState.builder()
                .sagaId(sagaId)
                .idempotencyKey(request.getIdempotencyKey())
                .senderWalletId(sender.getWalletId())
                .receiverWalletId(receiver.getWalletId())
                .amount(request.getAmount())
                .status(SagaStatus.INITIATED)
                .currentStep(SagaStep.DEBIT)
                .build();
        sagaStateRepository.save(saga);

        // Step 5 — Execute DEBIT step
        try {
            executeDebit(saga, sender, request.getAmount());
        } catch (Exception e) {
            log.error("Debit failed for saga {}: {}", sagaId, e.getMessage());
            saga.setStatus(SagaStatus.DEBIT_FAILED);
            saga.setFailureReason(e.getMessage());
            saga.setCurrentStep(SagaStep.ROLLBACK);
            sagaStateRepository.save(saga);
            writeOutboxEvent(saga, sender.getWalletId(), "DEBIT_FAILED");
            return toResponse(saga, request.getSenderUpiId(), request.getReceiverUpiId());
        }

        // Step 6 — Execute CREDIT step
        try {
            executeCredit(saga, receiver, request.getAmount());
        } catch (Exception e) {
            log.error("Credit failed for saga {}: {}", sagaId, e.getMessage());
            // Credit failed — must roll back the debit we already did
            saga.setStatus(SagaStatus.CREDIT_FAILED);
            saga.setCurrentStep(SagaStep.ROLLBACK);
            saga.setFailureReason(e.getMessage());
            sagaStateRepository.save(saga);
            writeOutboxEvent(saga, sender.getWalletId(), "CREDIT_FAILED");
            rollbackDebit(saga, sender, request.getAmount());
            return toResponse(saga, request.getSenderUpiId(), request.getReceiverUpiId());
        }

        // Step 7 — Mark saga COMPLETED
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep(SagaStep.CONFIRM);
        sagaStateRepository.save(saga);
        writeOutboxEvent(saga, sender.getWalletId(), "TRANSFER_COMPLETED");

        log.info("Saga {} completed successfully. Amount {} from {} to {}",
                sagaId, request.getAmount(),
                request.getSenderUpiId(), request.getReceiverUpiId());

        return toResponse(saga, request.getSenderUpiId(), request.getReceiverUpiId());
    }

    // ================================================================
    // DEBIT STEP — pessimistic lock on sender wallet
    // ================================================================
    @Transactional
    public void executeDebit(SagaState saga, Wallet sender, BigDecimal amount) {

        // Acquire row-level lock — SELECT FOR UPDATE
        // No other transaction can touch this wallet row until we commit
        Wallet lockedSender = walletRepository
                .findByIdWithLock(sender.getWalletId())
                .orElseThrow(() -> new RuntimeException("Sender wallet disappeared"));

        // Guard: wallet must be active
        if (lockedSender.getStatus() != Wallet.WalletStatus.ACTIVE)
            throw new RuntimeException("Sender wallet is not active");

        // Guard: sufficient balance — double spend check inside the lock
        // Checking balance OUTSIDE the lock would be a TOCTOU race condition
        if (lockedSender.getBalance().compareTo(amount) < 0)
            throw new RuntimeException("Insufficient balance. Available: "
                    + lockedSender.getBalance());

        BigDecimal balanceBefore = lockedSender.getBalance();
        BigDecimal balanceAfter  = balanceBefore.subtract(amount);

        lockedSender.setBalance(balanceAfter);
        walletRepository.save(lockedSender);

        // Write audit record
        saveHistory(lockedSender.getWalletId(), saga.getSagaId(),
                TransactionHistory.TxnType.DEBIT, amount, balanceBefore, balanceAfter);

        saga.setStatus(SagaStatus.DEBITED);
        saga.setCurrentStep(SagaStep.CREDIT);
        sagaStateRepository.save(saga);

        writeOutboxEvent(saga, lockedSender.getWalletId(), "DEBIT_COMPLETED");
        log.info("Debit complete for saga {}. New balance: {}", saga.getSagaId(), balanceAfter);
    }

    // ================================================================
    // CREDIT STEP — pessimistic lock on receiver wallet
    // ================================================================
    @Transactional
    public void executeCredit(SagaState saga, Wallet receiver, BigDecimal amount) {

        Wallet lockedReceiver = walletRepository
                .findByIdWithLock(receiver.getWalletId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet disappeared"));

        if (lockedReceiver.getStatus() != Wallet.WalletStatus.ACTIVE)
            throw new RuntimeException("Receiver wallet is not active");

        BigDecimal balanceBefore = lockedReceiver.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(amount);

        lockedReceiver.setBalance(balanceAfter);
        walletRepository.save(lockedReceiver);

        saveHistory(lockedReceiver.getWalletId(), saga.getSagaId(),
                TransactionHistory.TxnType.CREDIT, amount, balanceBefore, balanceAfter);

        saga.setStatus(SagaStatus.CREDITED);
        saga.setCurrentStep(SagaStep.CONFIRM);
        sagaStateRepository.save(saga);

        writeOutboxEvent(saga, lockedReceiver.getWalletId(), "CREDIT_COMPLETED");
        log.info("Credit complete for saga {}. New balance: {}", saga.getSagaId(), balanceAfter);
    }

    // ================================================================
    // ROLLBACK — re-credit sender if credit step failed
    // ================================================================
    @Transactional
    public void rollbackDebit(SagaState saga, Wallet sender, BigDecimal amount) {

        Wallet lockedSender = walletRepository
                .findByIdWithLock(sender.getWalletId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found during rollback"));

        BigDecimal balanceBefore = lockedSender.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(amount);

        lockedSender.setBalance(balanceAfter);
        walletRepository.save(lockedSender);

        saveHistory(lockedSender.getWalletId(), saga.getSagaId(),
                TransactionHistory.TxnType.ROLLBACK, amount, balanceBefore, balanceAfter);

        saga.setStatus(SagaStatus.ROLLBACK_COMPLETE);
        saga.setCurrentStep(SagaStep.ROLLBACK);
        sagaStateRepository.save(saga);

        writeOutboxEvent(saga, lockedSender.getWalletId(), "ROLLBACK_COMPLETE");
        log.info("Rollback complete for saga {}. Refunded: {}", saga.getSagaId(), amount);
    }

    // ================================================================
    // STATUS CHECK — frontend polls this
    // ================================================================
    @Transactional(readOnly = true)
    public TransferResponse getSagaStatus(String sagaId) {
        SagaState saga = sagaStateRepository.findById(sagaId)
                .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));

        Wallet sender   = walletRepository.findById(saga.getSenderWalletId()).orElse(null);
        Wallet receiver = walletRepository.findById(saga.getReceiverWalletId()).orElse(null);

        return toResponse(saga,
                sender   != null ? sender.getUpiId()   : "unknown",
                receiver != null ? receiver.getUpiId() : "unknown");
    }

    // ================================================================
    // HELPERS
    // ================================================================
    private void saveHistory(Long walletId, String sagaId,
                             TransactionHistory.TxnType type,
                             BigDecimal amount,
                             BigDecimal before, BigDecimal after) {
        TransactionHistory history = TransactionHistory.builder()
                .txnId(snowflakeIdGenerator.nextId())
                .walletId(walletId)
                .sagaId(sagaId)
                .txnType(type)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .build();
        historyRepository.save(history);
    }

    private void writeOutboxEvent(SagaState saga, Long walletId, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sagaId",           saga.getSagaId(),
                    "senderWalletId",   saga.getSenderWalletId(),
                    "receiverWalletId", saga.getReceiverWalletId(),
                    "amount",           saga.getAmount(),
                    "status",           saga.getStatus().name(),
                    "eventType",        eventType,
                    "timestamp",        LocalDateTime.now().toString()
            ));

            TransactionOutbox outbox = TransactionOutbox.builder()
                    .walletId(walletId)
                    .sagaId(saga.getSagaId())
                    .eventType(eventType)
                    .payload(payload)
                    .status(TransactionOutbox.OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(outbox);

        } catch (Exception e) {
            log.error("Failed to write outbox event for saga {}: {}",
                    saga.getSagaId(), e.getMessage());
        }
    }

    private TransferResponse toResponse(SagaState saga,
                                        String senderUpiId,
                                        String receiverUpiId) {
        return TransferResponse.builder()
                .sagaId(saga.getSagaId())
                .idempotencyKey(saga.getIdempotencyKey())
                .senderUpiId(senderUpiId)
                .receiverUpiId(receiverUpiId)
                .amount(saga.getAmount())
                .status(saga.getStatus().name())
                .currentStep(saga.getCurrentStep().name())
                .failureReason(saga.getFailureReason())
                .createdAt(saga.getCreatedAt())
                .build();
    }
}