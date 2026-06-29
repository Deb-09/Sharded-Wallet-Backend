package com.example.rubun.sharded_wallet.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TransferResponse {
    private String sagaId;
    private String idempotencyKey;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
    private String status;       // mirrors SagaStatus
    private String currentStep;  // mirrors SagaStep
    private String failureReason;
    private LocalDateTime createdAt;
}