package com.ewallet.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logging in caches the user's details, password hash included. A password change or reset has to
 * drop that entry, or the old password keeps working and the new one fails until it expires.
 */
class PasswordChangeLoginTest extends ApiTestSupport {

    @Test
    void afterChangingPasswordOnlyTheNewOneLogsIn() throws Exception {
        Session s = signupAndLogin("oldPass123");

        putJson("/users/me/change-password", s,
                Map.of("currentPassword", "oldPass123", "newPassword", "newPass456"))
                .andExpect(status().isOk());

        login(s.username(), "newPass456").andExpect(status().isOk());
        login(s.username(), "oldPass123").andExpect(status().isUnauthorized());
    }

    @Test
    void afterAResetOnlyTheNewPasswordLogsIn() throws Exception {
        Session s = signupAndLogin("oldPass123");
        String email = s.username() + "@test.io";

        String otp = dataOf(mvc.perform(post(API + "/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))))).path("devOtp").asText();
        mvc.perform(post(API + "/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "otp", otp, "newPassword", "resetPass789"))))
                .andExpect(status().isOk());

        login(s.username(), "resetPass789").andExpect(status().isOk());
        login(s.username(), "oldPass123").andExpect(status().isUnauthorized());
    }
}
