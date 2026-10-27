package com.ewallet.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Data Transfer Object for Signup request.
 *
 * <p>Deliberately has no {@code id} or {@code roles}: an id would make {@code save()} overwrite an
 * existing user, and client-chosen roles would allow self-promotion. Older clients that still send
 * {@code roles} are fine, since unknown JSON fields are ignored.
 */
@Data
public class SignupRequest {

    @Size(min = 3, max = 50, message = "{firstName.size}")
    @NotBlank(message = "{firstName.notblank}")
    private String firstName;

    @Size(min = 3, max = 50, message = "{lastName.size}")
    @NotBlank(message = "{lastName.notblank}")
    private String lastName;

    @Size(min = 3, max = 20, message = "{username.size}")
    @NotBlank(message = "{username.notblank}")
    private String username;

    @Email(message = "email.acceptable")
    @Size(min = 6, max = 50, message = "{email.size}")
    @NotBlank(message = "email.notblank}")
    private String email;

    @Size(min = 6, max = 100, message = "{password.size}")
    @NotBlank(message = "password.notblank}")
    private String password;
}
