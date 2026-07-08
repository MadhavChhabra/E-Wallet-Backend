package com.ewallet.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A saved payment card. For PCI safety we deliberately store only a masked
 * representation: the last four digits, the detected brand and the expiry. The
 * full PAN and the CVV are never persisted.
 */
@Getter
@Setter
@Entity
@Table(name = "card", indexes = {
        @Index(name = "idx_card_user", columnList = "user_id")
})
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_holder_name", nullable = false, length = 100)
    private String cardHolderName;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(nullable = false, length = 20)
    private String brand;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    /** Opaque reference standing in for the (never-stored) card. */
    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", referencedColumnName = "id")
    private BankAccount bankAccount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignToken() {
        if (token == null) {
            token = UUID.randomUUID().toString();
        }
    }
}
