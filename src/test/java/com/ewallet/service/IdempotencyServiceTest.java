package com.ewallet.service;

import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.IdempotencyConflictException;
import com.ewallet.model.IdempotencyRecord;
import com.ewallet.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
        idempotencyService = new IdempotencyService(idempotencyRecordRepository, clock);
    }

    @Test
    void execute_returnsCachedResponseWhenKeyExists() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey("key-1")
                .userId(1L)
                .requestHash("hash-1")
                .transactionId(99L)
                .build();

        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(existing));

        CommandResponse response = idempotencyService.execute(
                "key-1", 1L, "TRANSFER", "hash-1", () -> CommandResponse.builder().id(1L).build());

        assertThat(response.getId()).isEqualTo(99L);
        verify(idempotencyRecordRepository, never()).save(any());
    }

    @Test
    void execute_throwsWhenSameKeyDifferentPayload() {
        IdempotencyRecord existing = IdempotencyRecord.builder()
                .idempotencyKey("key-1")
                .userId(1L)
                .requestHash("hash-1")
                .transactionId(99L)
                .build();

        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, "key-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.execute(
                "key-1", 1L, "TRANSFER", "hash-2", () -> CommandResponse.builder().id(1L).build()))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void execute_persistsRecordForNewKey() {
        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, "key-2"))
                .thenReturn(Optional.empty());

        CommandResponse response = idempotencyService.execute(
                "key-2", 1L, "TRANSFER", "hash-1", () -> CommandResponse.builder().id(42L).build());

        assertThat(response.getId()).isEqualTo(42L);

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo(42L);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-2");
    }
}
