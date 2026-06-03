package com.ewallet.service;

import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.IdempotencyConflictException;
import com.ewallet.model.IdempotencyRecord;
import com.ewallet.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Clock clock;

    @Transactional
    public CommandResponse execute(
            String idempotencyKey,
            Long userId,
            String operation,
            String requestHash,
            Supplier<CommandResponse> action) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        validateKeyFormat(idempotencyKey);

        return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(existing -> handleExisting(existing, requestHash))
                .orElseGet(() -> executeAndRecord(idempotencyKey, userId, operation, requestHash, action));
    }

    public static String hashTransactionRequest(
            String fromIban, String toIban, String amount, Long typeId) {
        String payload = fromIban + "|" + toIban + "|" + amount + "|" + typeId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private CommandResponse handleExisting(IdempotencyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used with a different request payload");
        }
        log.info("Returning cached idempotent response for key={} transactionId={}",
                existing.getIdempotencyKey(), existing.getTransactionId());
        return CommandResponse.builder().id(existing.getTransactionId()).build();
    }

    private CommandResponse executeAndRecord(
            String idempotencyKey,
            Long userId,
            String operation,
            String requestHash,
            Supplier<CommandResponse> action) {

        CommandResponse result = action.get();

        IdempotencyRecord record = IdempotencyRecord.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .operation(operation)
                .requestHash(requestHash)
                .transactionId(result.getId())
                .createdAt(LocalDateTime.now(clock))
                .build();

        try {
            idempotencyRecordRepository.save(record);
        } catch (DataIntegrityViolationException ex) {
            return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .map(existing -> handleExisting(existing, requestHash))
                    .orElseThrow(() -> ex);
        }

        return result;
    }

    private void validateKeyFormat(String idempotencyKey) {
        if (idempotencyKey.length() > 64) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 64 characters");
        }
    }
}
