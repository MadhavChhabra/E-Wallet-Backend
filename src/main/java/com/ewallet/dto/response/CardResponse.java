package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CardResponse {

    private Long id;
    private String cardHolderName;
    private String maskedNumber;
    private String last4;
    private String brand;
    private String expiryDate;
    private Long bankAccountId;
    private String token;
}
