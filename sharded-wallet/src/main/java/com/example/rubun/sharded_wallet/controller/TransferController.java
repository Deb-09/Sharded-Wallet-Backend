package com.example.rubun.sharded_wallet.controller;

import com.example.rubun.sharded_wallet.dto.*;
import com.example.rubun.sharded_wallet.service.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final SagaOrchestrator sagaOrchestrator;

    // Initiate a transfer — this kicks off the full saga
    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @RequestBody TransferRequest request) {
        TransferResponse response = sagaOrchestrator.initiateSaga(request);
        return ResponseEntity.ok(
                ApiResponse.success("Transfer processed", response));
    }

    // Poll saga status — frontend calls this to track progress
    @GetMapping("/{sagaId}/status")
    public ResponseEntity<ApiResponse<TransferResponse>> getStatus(
            @PathVariable String sagaId) {
        TransferResponse response = sagaOrchestrator.getSagaStatus(sagaId);
        return ResponseEntity.ok(
                ApiResponse.success("Saga status fetched", response));
    }
}