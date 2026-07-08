package com.ewallet.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request to create a Razorpay top-up order. {@code amount} is in major currency
 * units (e.g. rupees); it is converted to the smallest unit (paise) server-side.
 */
@Data
public class CreateOrderRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    private BigDecimal amount;

    @NotBlank(message = "Target account IBAN is required")
    private String toBankAccountIban;
}
