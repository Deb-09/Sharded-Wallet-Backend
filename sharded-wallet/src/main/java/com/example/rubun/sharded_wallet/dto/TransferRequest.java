package com.example.rubun.sharded_wallet.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TransferRequest {

    private String idempotencyKey;
    private String senderUpiId;
    private String receiverUpiId;
    private BigDecimal amount;
}

// Client generates this UUID and sends it with every request
// If the same key comes in twice, we return the existing result
// This is our API-layer double spend guard
