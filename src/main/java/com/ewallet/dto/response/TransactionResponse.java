package com.ewallet.dto.response;

import com.ewallet.model.Status;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data Transfer Object for Transaction response
 */
@Data
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private String description;
    private String createdAt;
    private UUID referenceNumber;
    private Status status;
    private TransactionPartyResponse fromBankAccount;
    private TransactionPartyResponse toBankAccount;
    private TypeResponse type;
}
