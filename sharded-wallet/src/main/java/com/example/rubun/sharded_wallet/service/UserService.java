package com.example.rubun.sharded_wallet.service;

import com.example.rubun.sharded_wallet.config.SnowflakeIdGenerator;
import com.example.rubun.sharded_wallet.dto.*;
import com.example.rubun.sharded_wallet.entity.User;
import com.example.rubun.sharded_wallet.entity.Wallet;
import com.example.rubun.sharded_wallet.repository.UserRepository;
import com.example.rubun.sharded_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository       userRepository;
    private final WalletRepository     walletRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Guard: duplicate username or email
        if (userRepository.existsByUsername(request.getUsername()))
            throw new RuntimeException("Username already taken");
        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("Email already registered");

        // Create user — password is BCrypt hashed, never stored plain
        Long userId = snowflakeIdGenerator.nextId();
        User user = User.builder()
                .userId(userId)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);

        // Every new user gets a wallet with $1000 dummy balance
        Long walletId = snowflakeIdGenerator.nextId();
        String upiId  = request.getUsername().toLowerCase() + "@wallet";

        Wallet wallet = Wallet.builder()
                .walletId(walletId)
                .userId(userId)
                .upiId(upiId)
                .balance(new BigDecimal("1000.00"))
                .status(Wallet.WalletStatus.ACTIVE)
                .build();
        walletRepository.save(wallet);

        // Return JWT immediately — user is logged in after register
        String token = jwtService.generateToken(user.getUsername(), userId);
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .upiId(upiId)
                .userId(userId)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
            throw new RuntimeException("Invalid password");

        Wallet wallet = walletRepository.findByUserId(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        String token = jwtService.generateToken(user.getUsername(), user.getUserId());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .upiId(wallet.getUpiId())
                .userId(user.getUserId())
                .build();
    }
}