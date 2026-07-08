package com.ewallet.controller;

import com.ewallet.dto.request.CreateOrderRequest;
import com.ewallet.dto.request.VerifyPaymentRequest;
import com.ewallet.dto.response.ApiResponse;
import com.ewallet.dto.response.PaymentVerificationResponse;
import com.ewallet.dto.response.RazorpayOrderResponse;
import com.ewallet.service.RazorpayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;

import static com.ewallet.common.Constants.SUCCESS;

/**
 * Razorpay-backed wallet top-up. All endpoints require an authenticated user.
 */
@RestController
@RequestMapping("/api/v1/payments/razorpay")
@RequiredArgsConstructor
public class PaymentController {

    private final Clock clock;
    private final RazorpayService razorpayService;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/order")
    public ResponseEntity<ApiResponse<RazorpayOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        RazorpayOrderResponse response = razorpayService.createOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentVerificationResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        PaymentVerificationResponse response = razorpayService.verifyPayment(request);
        return ResponseEntity.ok(new ApiResponse<>(Instant.now(clock).toEpochMilli(), SUCCESS, response));
    }

    /**
     * Razorpay server-to-server webhook. Unauthenticated (Razorpay has no JWT);
     * the raw body is HMAC-verified against the configured webhook secret. This
     * is the authoritative crediting path.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        razorpayService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
