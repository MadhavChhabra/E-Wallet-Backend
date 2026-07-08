package com.ewallet.controller;

import com.ewallet.dto.request.CardRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.CardResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static com.ewallet.common.Constants.SUCCESS;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final Clock clock;
    private final CardService cardService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CardResponse>>> findAll() {
        List<CardResponse> response = cardService.findAllForCurrentUser();
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CommandResponse>> create(@Valid @RequestBody CardRequest request) {
        CommandResponse response = cardService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable long id) {
        cardService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
