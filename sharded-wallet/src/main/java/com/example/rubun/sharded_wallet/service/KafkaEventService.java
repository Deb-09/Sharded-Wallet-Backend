package com.example.rubun.sharded_wallet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaEventService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    // Topics — one per saga step
    public static final String TOPIC_DEBIT    = "wallet.debit";
    public static final String TOPIC_CREDIT   = "wallet.credit";
    public static final String TOPIC_ROLLBACK = "wallet.rollback";
    public static final String TOPIC_COMPLETE = "wallet.complete";

    public void publishDebitEvent(String sagaId, String payload) {
        publish(TOPIC_DEBIT, sagaId, payload);
    }

    public void publishCreditEvent(String sagaId, String payload) {
        publish(TOPIC_CREDIT, sagaId, payload);
    }

    public void publishRollbackEvent(String sagaId, String payload) {
        publish(TOPIC_ROLLBACK, sagaId, payload);
    }

    public void publishCompleteEvent(String sagaId, String payload) {
        publish(TOPIC_COMPLETE, sagaId, payload);
    }

    private void publish(String topic, String key, String payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic {}: {}", topic, ex.getMessage());
                    } else {
                        log.info("Published to topic {} partition {} offset {}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}