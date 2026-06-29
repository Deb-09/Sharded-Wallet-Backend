package com.example.rubun.sharded_wallet.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class WalletResponse {
    private Long walletId;
    private String upiId;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;

}
