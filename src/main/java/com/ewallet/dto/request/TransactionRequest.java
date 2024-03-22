package com.ewallet.dto.request;

import com.ewallet.model.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for Transaction request
 */
@Data
public class TransactionRequest {

    private Long id;

    @NotNull(message = "{amount.notnull}")
    private BigDecimal amount;

    @Size(max = 50, message = "{description.size}")
    private String description;

    private Instant createdAt;

    private UUID referenceNumber;

    private Status status;

    @NotBlank(message = "fromBankAccountiban.notblank}")
    private String fromBankAccountiban;

    @NotBlank(message = "{toBankAccountiban.notblank}")
    private String toBankAccountiban;

    @NotNull(message = "{typeId.notnull}")
    private Long typeId;
}
