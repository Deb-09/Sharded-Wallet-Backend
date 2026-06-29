package com.example.rubun.sharded_wallet.dto;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LoginRequest {
   private String username;
   private String password;
}
