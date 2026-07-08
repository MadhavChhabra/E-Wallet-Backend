package com.ewallet.controller;

import com.ewallet.dto.request.OtpVerifyRequest;
import com.ewallet.dto.request.PasswordResetConfirmRequest;
import com.ewallet.dto.request.PasswordResetRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.ewallet.common.Constants.SUCCESS;

/**
 * Public password-reset flow: request OTP → verify OTP → confirm new password.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final Clock clock;
    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> request(
            @Valid @RequestBody PasswordResetRequest request) {
        String otp = passwordResetService.requestReset(request.getEmail());
        Map<String, Object> data = new HashMap<>();
        data.put("message", "If the email is registered, a reset code has been sent.");
        if (otp != null) {
            // Only populated when app.password-reset.expose-otp=true (dev/test).
            data.put("devOtp", otp);
        }
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, data));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@Valid @RequestBody OtpVerifyRequest request) {
        passwordResetService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getEmail(), request.getOtp(), request.getNewPassword());
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS));
    }
}
