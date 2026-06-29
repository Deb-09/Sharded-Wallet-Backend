package com.example.rubun.sharded_wallet.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;        // JWT token
    private String username;
    private String upiId;
    private Long userId;
}