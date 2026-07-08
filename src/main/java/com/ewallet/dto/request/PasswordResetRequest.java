package com.ewallet.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "A valid email is required")
    private String email;
}
