package com.ewallet.controller;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.request.BankAccountRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.BankAccountResponse;
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

@CrossOrigin(origins = "http://localhost:3306/")
@RestController
@RequestMapping("/api/v1/BankAccounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final Clock clock;
    private final BankAccountService BankAccountService;

    /**
     * Fetches a single BankAccount by the given id
     *
     * @param id
     * @return BankAccountResponse wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> findById(@PathVariable long id) {
        final BankAccountResponse response = BankAccountService.findById(id);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Fetches a single BankAccount by the given iban
     *
     * @param iban
     * @return BankAccountResponse wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @GetMapping("/iban/{iban}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> findByiban(@PathVariable String iban) {
        final BankAccountResponse response = BankAccountService.findByiban(iban);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Fetches a single BankAccount by the given userId
     *
     * @param userId
     * @return BankAccountResponse wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<List<BankAccountResponse>>> findByUserId(@PathVariable long userId) {
        final List<BankAccountResponse> response = BankAccountService.findByUserId(userId);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Fetches all BankAccounts based on the given paging and sorting parameters
     *
     * @param pageable
     * @return List of BankAccountResponse wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BankAccountResponse>>> findAll(Pageable pageable) {
        final Page<BankAccountResponse> response = BankAccountService.findAll(pageable);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Creates a new BankAccount using the given request parameters
     *
     * @param request
     * @return id of the created BankAccount wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @PostMapping
    public ResponseEntity<ApiResponse<CommandResponse>> create(@Valid @RequestBody BankAccountRequest request) {
        final CommandResponse response = BankAccountService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Transfer funds between BankAccounts
     *
     * @param request
     * @return id of the created transaction wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<CommandResponse>> transferFunds(@Valid @RequestBody TransactionRequest request) {
        final CommandResponse response = BankAccountService.transferFunds(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Adds funds to the given BankAccount
     *
     * @param request
     * @return id of the created transaction wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @PostMapping("/addFunds")
    public ResponseEntity<ApiResponse<CommandResponse>> addFunds(@Valid @RequestBody TransactionRequest request) {
        final CommandResponse response = BankAccountService.addFunds(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Withdraw funds from the given BankAccount
     *
     * @param request
     * @return id of the created transaction wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @PostMapping("/withdrawFunds")
    public ResponseEntity<ApiResponse<CommandResponse>> withdrawFunds(@Valid @RequestBody TransactionRequest request) {
        final CommandResponse response = BankAccountService.withdrawFunds(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Updates BankAccount using the given request parameters
     *
     * @param request
     * @return id of the updated BankAccount wrapped by ResponseEntity<ApiResponse<T>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @PutMapping
    public ResponseEntity<ApiResponse<CommandResponse>> update(@Valid @RequestBody BankAccountRequest request) {
        final CommandResponse response = BankAccountService.update(request);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Deletes BankAccount by the given id
     *
     * @param id
     * @return ResponseEntity<ApiResponse < Void>>
     */
    @PreAuthorize("hasRole(T(com.ewallet.model.RoleType).ROLE_USER)")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteById(@PathVariable long id) {
        BankAccountService.deleteById(id);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
}
