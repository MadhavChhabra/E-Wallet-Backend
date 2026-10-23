package com.ewallet.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetAttemptsTest extends ApiTestSupport {

    @Test
    void wrongCodesAreCountedSoTheRealCodeStopsWorkingAfterTheLimit() throws Exception {
        String username = uniqueName();
        signup(signupBody(username, "secret123")).andExpect(status().isCreated());
        String email = username + "@test.io";

        String otp = dataOf(mvc.perform(post(API + "/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))))).path("devOtp").asText();
        String wrong = otp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verify(email, wrong).andExpect(status().isBadRequest());
        }

        // Five failures used up the code: even the right one must now be refused.
        verify(email, otp).andExpect(status().isTooManyRequests());
    }

    private org.springframework.test.web.servlet.ResultActions verify(String email, String otp) throws Exception {
        return mvc.perform(post(API + "/auth/password-reset/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "otp", otp))));
    }
}
