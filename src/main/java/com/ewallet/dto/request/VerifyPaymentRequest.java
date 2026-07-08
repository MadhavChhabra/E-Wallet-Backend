package com.ewallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Payload returned by the Razorpay Checkout on the client, submitted back to the
 * server for signature verification before the wallet is credited.
 */
@Data
public class VerifyPaymentRequest {

    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;
}
