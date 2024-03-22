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
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 50)
    private String description;

    @Column(nullable = true)
    private LocalDateTime createdAt;

    @Column(nullable = false, unique = true)
    private UUID referenceNumber;

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_BankAccount_id", referencedColumnName = "id", nullable = false)
    private BankAccount fromBankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_BankAccount_id", referencedColumnName = "id", nullable = false)
    private BankAccount toBankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", referencedColumnName = "id", nullable = false)
    private Type type;
}
