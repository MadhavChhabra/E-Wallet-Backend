package com.ewallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Card details captured on the client. Only a masked representation is persisted;
 * the CVV and full PAN are discarded server-side immediately after validation.
 */
@Data
public class CardRequest {

    @NotBlank(message = "Cardholder name is required")
    @Size(min = 2, max = 100, message = "Cardholder name must be between 2 and 100 characters")
    private String cardHolderName;

    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits")
    private String cardNumber;

    /** Format MM/YY or MM/YYYY. */
    @NotBlank(message = "Expiry date is required")
    @Pattern(regexp = "(0[1-9]|1[0-2])/(\\d{2}|\\d{4})", message = "Expiry must be in MM/YY format")
    private String expiryDate;

    @Pattern(regexp = "\\d{3,4}", message = "CVV must be 3 or 4 digits")
    private String cvv;

    private Long bankAccountId;
}
