package com.ewallet.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a Razorpay top-up order and its lifecycle. Persisting the order
 * server-side lets us (a) validate the amount at capture time against what we
 * originally created, and (b) make wallet crediting idempotent per payment.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_order", indexes = {
        @Index(name = "idx_payment_order_rzp_order", columnList = "razorpay_order_id", unique = true),
        @Index(name = "idx_payment_order_user", columnList = "user_id")
})
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 64)
    private String razorpayPaymentId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "target_iban", nullable = false, length = 34)
    private String targetIban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "wallet_transaction_id")
    private Long walletTransactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
