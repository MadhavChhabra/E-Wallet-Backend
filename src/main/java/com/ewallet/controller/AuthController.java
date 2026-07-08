package com.ewallet.controller;

import com.ewallet.dto.request.GoogleLoginRequest;
import com.ewallet.dto.request.LoginRequest;
import com.ewallet.dto.request.SignupRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.JwtResponse;
import com.ewallet.service.AuthService;
import com.ewallet.service.DemoService;
import com.ewallet.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;

import static com.ewallet.common.Constants.SUCCESS;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final Clock clock;
    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final DemoService demoService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest request) {
        JwtResponse response = authService.login(request);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<CommandResponse>> signup(@Valid @RequestBody SignupRequest request) {
        CommandResponse response = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<JwtResponse>> getAccessToken(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        JwtResponse response = authService.getAccessTokenUsingRefreshToken(authorizationHeader);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        authService.logout();
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS));
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<JwtResponse>> googleLogin(
            @Valid @RequestBody GoogleLoginRequest request) {
        JwtResponse response = googleAuthService.loginWithGoogle(request.getIdToken());
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * One-click demo: provisions a fresh, pre-populated account and returns tokens,
     * so a visitor lands straight on a filled dashboard.
     */
    @PostMapping("/demo")
    public ResponseEntity<ApiResponse<JwtResponse>> demo() {
        JwtResponse response = demoService.createDemoSession();
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }
}
