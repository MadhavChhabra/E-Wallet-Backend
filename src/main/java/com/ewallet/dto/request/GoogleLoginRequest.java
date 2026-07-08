package com.ewallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Google Sign-In: the client posts the Google-issued ID token for server-side
 * verification.
 */
@Data
public class GoogleLoginRequest {

    @NotBlank(message = "idToken is required")
    private String idToken;
}
