package com.ewallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Authenticated password change: the user proves knowledge of their current
 * password before setting a new one.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String newPassword;
}
