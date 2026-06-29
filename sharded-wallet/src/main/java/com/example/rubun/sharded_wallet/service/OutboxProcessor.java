package com.example.rubun.sharded_wallet.service;

import com.example.rubun.sharded_wallet.entity.TransactionOutbox;
import com.example.rubun.sharded_wallet.repository.TransactionOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final TransactionOutboxRepository outboxRepository;
    private final KafkaEventService           kafkaEventService;

    // Runs every 5 seconds — picks up PENDING outbox rows
    // and publishes them to Kafka
    // If Kafka publish succeeds  → mark PUBLISHED
    // If Kafka publish fails     → mark FAILED (will retry next cycle)
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutbox() {
        List<TransactionOutbox> pendingEvents = outboxRepository.findPendingOutboxEvents();

        if (pendingEvents.isEmpty()) return;

        log.info("OutboxProcessor: found {} pending events", pendingEvents.size());

        for (TransactionOutbox event : pendingEvents) {
            try {
                publishToKafka(event);
                event.setStatus(TransactionOutbox.OutboxStatus.PUBLISHED);
                event.setProcessedAt(LocalDateTime.now());
                log.info("Outbox event {} published to Kafka topic for saga {}",
                        event.getOutboxId(), event.getSagaId());
            } catch (Exception e) {
                event.setStatus(TransactionOutbox.OutboxStatus.FAILED);
                log.error("Failed to publish outbox event {} : {}",
                        event.getOutboxId(), e.getMessage());
            }
            outboxRepository.save(event);
        }
    }

    private void publishToKafka(TransactionOutbox event) {
        switch (event.getEventType()) {
            case "DEBIT_COMPLETED"    -> kafkaEventService
                    .publishDebitEvent(event.getSagaId(), event.getPayload());
            case "CREDIT_COMPLETED"   -> kafkaEventService
                    .publishCreditEvent(event.getSagaId(), event.getPayload());
            case "ROLLBACK_COMPLETE",
                 "DEBIT_FAILED",
                 "CREDIT_FAILED"      -> kafkaEventService
                    .publishRollbackEvent(event.getSagaId(), event.getPayload());
            case "TRANSFER_COMPLETED" -> kafkaEventService
                    .publishCompleteEvent(event.getSagaId(), event.getPayload());
            default -> log.warn("Unknown event type: {}", event.getEventType());
        }
    }
}