package com.example.rubun.sharded_wallet.dto;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder

public class RegisterRequest {
    private String username;
    private String email;
    private String password;
}
