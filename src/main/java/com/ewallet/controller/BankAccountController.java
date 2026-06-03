package com.ewallet.controller;

import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.BankAccountResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.service.BankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static com.ewallet.common.Constants.SUCCESS;
import static com.ewallet.common.MdcConstants.IDEMPOTENCY_KEY_HEADER;

@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final Clock clock;
    private final BankAccountService bankAccountService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> findById(@PathVariable long id) {
        BankAccountResponse response = bankAccountService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/iban/{iban}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> findByIban(@PathVariable String iban) {
        BankAccountResponse response = bankAccountService.findByIban(iban);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> findByUserId(@PathVariable long userId) {
        List<BankAccountResponse> response = bankAccountService.findByUserId(userId);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BankAccountResponse>>> findAll(Pageable pageable) {
        Page<BankAccountResponse> response = bankAccountService.findAllForCurrentUser(pageable);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CommandResponse>> create(@Valid @RequestBody BankAccountRequest request) {
        CommandResponse response = bankAccountService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<CommandResponse>> transferFunds(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {
        CommandResponse response = bankAccountService.transferFunds(request, idempotencyKey);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/add-funds")
    public ResponseEntity<ApiResponse<CommandResponse>> addFunds(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {
        CommandResponse response = bankAccountService.addFunds(request, idempotencyKey);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/withdraw-funds")
    public ResponseEntity<ApiResponse<CommandResponse>> withdrawFunds(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {
        CommandResponse response = bankAccountService.withdrawFunds(request, idempotencyKey);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommandResponse>> update(
            @PathVariable long id,
            @Valid @RequestBody BankAccountRequest request) {
        request.setId(id);
        CommandResponse response = bankAccountService.update(request);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id) {
        bankAccountService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
