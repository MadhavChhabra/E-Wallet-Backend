package com.ewallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signup must only ever create a new, ordinary user, whatever extra fields the body carries. */
class SignupHardeningTest extends ApiTestSupport {

    @Test
    void anIdInTheBodyCannotOverwriteAnExistingUser() throws Exception {
        Session victim = signupAndLogin("victimPass1");

        String attacker = uniqueName();
        Map<String, Object> body = signupBody(attacker, "attackPass1");
        body.put("id", victim.userId());
        JsonNode created = dataOf(signup(body).andExpect(status().isCreated()));

        assertThat(created.path("id").asLong()).isNotEqualTo(victim.userId());
        login(victim.username(), "victimPass1").andExpect(status().isOk());
    }

    @Test
    void requestedRolesAreIgnoredAndEveryoneGetsRoleUser() throws Exception {
        String username = uniqueName();
        Map<String, Object> body = signupBody(username, "secret123");
        body.put("roles", List.of("ROLE_ADMIN"));
        signup(body).andExpect(status().isCreated());

        JsonNode roles = dataOf(login(username, "secret123")).path("roles");
        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).asText()).isEqualTo("ROLE_USER");
    }

    @Test
    void signupWithoutRolesWorks() throws Exception {
        Map<String, Object> body = signupBody(uniqueName(), "secret123");
        body.remove("roles");
        signup(body).andExpect(status().isCreated());
    }
}
