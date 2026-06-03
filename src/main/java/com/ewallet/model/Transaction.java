package com.ewallet.model;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@EqualsAndHashCode(of = {"referenceNumber"})
@Table(indexes = {
        @Index(name = "idx_tx_from_account", columnList = "from_bank_account_id"),
        @Index(name = "idx_tx_to_account", columnList = "to_bank_account_id"),
        @Index(name = "idx_tx_created_at", columnList = "created_at")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 50)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, unique = true)
    private UUID referenceNumber;

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "operation_type", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private OperationType operationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_bank_account_id", referencedColumnName = "id", nullable = false)
    private BankAccount fromBankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_bank_account_id", referencedColumnName = "id", nullable = false)
    private BankAccount toBankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", referencedColumnName = "id", nullable = false)
    private Type type;
}
