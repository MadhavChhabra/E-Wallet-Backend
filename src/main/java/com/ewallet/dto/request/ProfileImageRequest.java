package com.ewallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Associates a previously uploaded image (by its opaque stored filename) with
 * the current user's profile.
 */
@Data
public class ProfileImageRequest {

    @NotBlank(message = "filename is required")
    @Size(max = 100, message = "filename is too long")
    private String filename;
}
