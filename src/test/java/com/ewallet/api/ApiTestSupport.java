package com.ewallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base for HTTP-level tests: the full Spring context on H2, driven through the real filter chain,
 * controllers, services and transactions. Mocked unit tests cannot see rollbacks, caches or SQL,
 * which is exactly where the bugs these tests guard against lived.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.login.max-requests=10000",
        "app.rate-limit.transfer.max-requests=10000",
        "app.password-reset.expose-otp=true"
})
abstract class ApiTestSupport {

    protected static final String API = "/api/v1";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected record Session(long userId, String username, String token) {
    }

    protected static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected ResultActions signup(Map<String, Object> body) throws Exception {
        return mvc.perform(post(API + "/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** The body the Flutter app sends today, roles included. */
    protected Map<String, Object> signupBody(String username, String password) {
        return new java.util.HashMap<>(Map.of(
                "firstName", "Test",
                "lastName", "User",
                "username", username,
                "email", username + "@test.io",
                "password", password,
                "roles", java.util.List.of("ROLE_USER")));
    }

    protected ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post(API + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    protected Session signupAndLogin(String password) throws Exception {
        String username = uniqueName();
        signup(signupBody(username, password)).andReturn();
        JsonNode data = dataOf(login(username, password));
        return new Session(data.path("id").asLong(), username, data.path("token").asText());
    }

    /** A fresh demo user: two funded accounts and a little history, created through the real services. */
    protected Session demoSession() throws Exception {
        JsonNode data = dataOf(mvc.perform(post(API + "/auth/demo")));
        return new Session(data.path("id").asLong(), data.path("username").asText(), data.path("token").asText());
    }

    protected JsonNode accounts(Session s) throws Exception {
        return dataOf(mvc.perform(authed(get(API + "/bank-accounts/users/" + s.userId()), s)));
    }

    protected JsonNode transactions(Session s) throws Exception {
        return dataOf(mvc.perform(authed(get(API + "/transactions/users/" + s.userId() + "?page=0&size=50"), s)))
                .path("content");
    }

    protected ResultActions postJson(String path, Session s, Object body) throws Exception {
        return mvc.perform(authed(post(API + path), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected ResultActions putJson(String path, Session s, Object body) throws Exception {
        return mvc.perform(authed(put(API + path), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder req, Session s) {
        return req.header("Authorization", "Bearer " + s.token());
    }

    protected JsonNode dataOf(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
