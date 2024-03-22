package com.ewallet.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Data Transfer Object for BankAccount response
 */
@Data
public class BankAccountResponse {

    private Long id;
    private String iban;
    private String name;
    private BigDecimal balance;
    private com.ewallet.dto.response.UserResponse user;
}
