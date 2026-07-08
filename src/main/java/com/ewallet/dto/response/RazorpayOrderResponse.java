package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Everything the Flutter Razorpay Checkout needs to open the payment sheet.
 * {@code keyId} is the public test key; the secret never leaves the server.
 */
@Getter
@Builder
public class RazorpayOrderResponse {

    private String orderId;
    private String keyId;
    private Long amountInPaise;
    private BigDecimal amount;
    private String currency;
    private String receipt;
}
