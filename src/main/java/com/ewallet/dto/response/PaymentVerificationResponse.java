package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentVerificationResponse {

    private String status;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private Long walletTransactionId;
    private BigDecimal amountCredited;
}
