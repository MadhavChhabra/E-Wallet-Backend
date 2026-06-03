package com.ewallet.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "idempotency_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String operation;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
