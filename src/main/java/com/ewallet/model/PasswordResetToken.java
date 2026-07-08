package com.ewallet.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One-time password (OTP) issued for a password reset. The OTP itself is stored
 * only as a BCrypt hash; the plaintext is delivered out-of-band via email.
 */
@Getter
@Setter
@Entity
@Table(name = "password_reset_token", indexes = {
        @Index(name = "idx_pwd_reset_user", columnList = "user_id")
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(nullable = false)
    private int attempts;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
