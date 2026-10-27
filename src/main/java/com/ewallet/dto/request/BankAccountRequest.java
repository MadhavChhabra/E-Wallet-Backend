package com.ewallet.dto.request;

import com.ewallet.validator.ValidIban;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for BankAccount request.
 *
 * <p>There is no balance here on purpose: an account opens empty and its balance changes only
 * through money operations. Older clients that still send {@code balance} are fine, since unknown
 * JSON fields are ignored.
 */
@Data
public class BankAccountRequest {

    /** Taken from the URL on update; never read from the body. */
    @JsonIgnore
    private Long id;

    @ValidIban(message = "{iban.valid}")
    @NotBlank(message = "{iban.notblank}")
    private String iban;

    @Size(min = 3, max = 50, message = "{name.size}")
    @NotBlank(message = "{name.notblank}")
    private String name;

    @NotNull(message = "{userId.notnull}")
    private Long userId;
}
