package com.ewallet.service;

import com.ewallet.model.PasswordResetToken;
import com.ewallet.model.User;
import com.ewallet.repository.PasswordResetTokenRepository;
import com.ewallet.repository.RefreshTokenRepository;
import com.ewallet.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Password reset via emailed one-time password (OTP).
 *
 * <p>Security properties:
 * <ul>
 *   <li>OTPs are stored only as BCrypt hashes.</li>
 *   <li>Requesting a reset for an unknown email returns success without sending
 *       anything, so the endpoint does not leak which emails are registered.</li>
 *   <li>OTPs expire and are attempt-limited to resist brute force.</li>
 *   <li>On successful reset, all refresh tokens are revoked.</li>
 * </ul>
 */
@Slf4j(topic = "PasswordResetService")
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    @Value("${app.password-reset.otp-length:6}")
    private int otpLength;

    @Value("${app.password-reset.otp-expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.password-reset.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.password-reset.expose-otp:false}")
    private boolean exposeOtp;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                MailService mailService) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
    }

    /**
     * Issues an OTP for the given email. Always succeeds from the caller's
     * perspective. Returns the OTP only when {@code app.password-reset.expose-otp}
     * is enabled (dev/test), otherwise {@code null}.
     */
    @Transactional
    public String requestReset(String email) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email.trim());
        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for unknown email (ignored)");
            return null;
        }

        User user = maybeUser.get();
        tokenRepository.consumeAllForUser(user);

        String otp = generateOtp();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setOtpHash(passwordEncoder.encode(otp));
        token.setExpiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES));
        token.setConsumed(false);
        token.setAttempts(0);
        token.setCreatedAt(Instant.now());
        tokenRepository.save(token);

        mailService.send(user.getEmail(), "Your E-Wallet password reset code",
                "Your one-time password is " + otp + ". It expires in " + otpExpiryMinutes + " minutes. "
                        + "If you did not request this, you can ignore this email.");

        log.info("Issued password reset OTP for userId={}", user.getId());
        return exposeOtp ? otp : null;
    }

    /**
     * Verifies an OTP without consuming it, so the client can gate the
     * new-password screen. Increments the attempt counter on failure.
     *
     * <p>A wrong code is reported by throwing, and a plain {@code @Transactional}
     * would roll back the attempt it just recorded, leaving guessing unlimited.
     * {@code noRollbackFor} keeps the attempt (and the lock-out) committed.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verifyOtp(String email, String otp) {
        validateOtp(email, otp);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void confirmReset(String email, String otp, String newPassword) {
        User user = validateOtp(email, otp).getUser();

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);

        tokenRepository.consumeAllForUser(user);
        int revoked = refreshTokenRepository.revokeAllForUser(user);

        log.info("Password reset completed for userId={} (revoked {} session(s))", user.getId(), revoked);
    }

    private PasswordResetToken validateOtp(String email, String otp) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code"));

        PasswordResetToken token = tokenRepository
                .findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code"));

        if (token.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }

        if (token.getAttempts() >= maxAttempts) {
            token.setConsumed(true);
            tokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Request a new code.");
        }

        if (!passwordEncoder.matches(otp.trim(), token.getOtpHash())) {
            token.setAttempts(token.getAttempts() + 1);
            tokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }

        return token;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
