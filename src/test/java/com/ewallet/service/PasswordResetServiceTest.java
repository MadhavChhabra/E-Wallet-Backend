package com.ewallet.service;

import com.ewallet.model.PasswordResetToken;
import com.ewallet.model.User;
import com.ewallet.repository.PasswordResetTokenRepository;
import com.ewallet.repository.RefreshTokenRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.UserDetailsCacheEvictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MailService mailService;
    @Mock
    private UserDetailsCacheEvictor userDetailsCacheEvictor;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, refreshTokenRepository,
                passwordEncoder, mailService, userDetailsCacheEvictor);
        ReflectionTestUtils.setField(service, "otpLength", 6);
        ReflectionTestUtils.setField(service, "otpExpiryMinutes", 10L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "exposeOtp", true);
    }

    @Test
    void requestReset_unknownEmail_isNoOpAndDoesNotLeak() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        String otp = service.requestReset("ghost@example.com");

        assertThat(otp).isNull();
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(mailService);
    }

    @Test
    void requestReset_knownEmail_savesHashedOtpAndEmailsUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED_OTP");

        String otp = service.requestReset("user@example.com");

        assertThat(otp).isNotNull().hasSize(6).matches("\\d{6}");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getOtpHash()).isEqualTo("HASHED_OTP");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.isConsumed()).isFalse();

        verify(mailService).send(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    void confirmReset_wrongOtp_incrementsAttemptsAndThrows() {
        User user = new User();
        user.setId(1L);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setOtpHash("HASHED");
        token.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        token.setAttempts(0);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmReset("user@example.com", "000000", "newSecret1"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(token.getAttempts()).isEqualTo(1);
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmReset_validOtp_updatesPasswordAndRevokesSessions() {
        User user = new User();
        user.setId(1L);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setOtpHash("HASHED");
        token.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndConsumedFalseOrderByCreatedAtDesc(user)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "HASHED")).thenReturn(true);
        when(passwordEncoder.encode("newSecret1")).thenReturn("NEW_HASH");

        service.confirmReset("user@example.com", "123456", "newSecret1");

        assertThat(user.getPassword()).isEqualTo("NEW_HASH");
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllForUser(user);
        verify(tokenRepository).consumeAllForUser(user);
    }
}
