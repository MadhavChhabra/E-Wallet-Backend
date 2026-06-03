package com.ewallet.controller;

import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.ewallet.common.Constants.SUCCESS;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final Clock clock;
    private final TransactionService transactionService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> findById(@PathVariable long id) {
        TransactionResponse response = transactionService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/references/{referenceNumber}")
    public ResponseEntity<ApiResponse<TransactionResponse>> findByReferenceNumber(@PathVariable UUID referenceNumber) {
        TransactionResponse response = transactionService.findByReferenceNumber(referenceNumber);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> findAllByUserId(
            @PathVariable Long userId,
            Pageable pageable) {
        Page<TransactionResponse> response = transactionService.findAllByUserId(userId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> findAll(Pageable pageable) {
        Page<TransactionResponse> response = transactionService.findAllForCurrentUser(pageable);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }
}
