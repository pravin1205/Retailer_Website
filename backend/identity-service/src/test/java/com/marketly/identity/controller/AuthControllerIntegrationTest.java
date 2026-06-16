package com.marketly.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketly.identity.dto.LoginRequest;
import com.marketly.identity.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test for the Auth API.
 * Uses a real PostgreSQL instance via Testcontainers.
 * Kafka is mocked/disabled for speed — only the DB layer is real.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.kafka.bootstrap-servers=",         // disable Kafka
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "eureka.client.enabled=false",
        "spring.flyway.baseline-on-migrate=true"
    }
)
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("marketly_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.schemas",      () -> "identity");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "identity");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    // ── Register ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/register → 201 with userId")
    void register_shouldCreate201() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("jane@test.com");
        req.setPassword("Password@1");
        req.setFirstName("Jane");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").isNotEmpty())
            .andExpect(jsonPath("$.data.email").value("jane@test.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with duplicate email → 409")
    void register_duplicateEmail_shouldReturn409() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@test.com");
        req.setPassword("Password@1");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isCreated());

        // Second request with same email
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with weak password → 422")
    void register_weakPassword_shouldReturn422() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("weak@test.com");
        req.setPassword("short");

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details[0].field").value("password"));
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/login → 200 with tokens")
    void login_shouldReturnTokens() throws Exception {
        // Register first
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail("loginuser@test.com");
        reg.setPassword("Password@1");
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(reg)))
            .andExpect(status().isCreated());

        // Then login
        LoginRequest login = new LoginRequest();
        login.setEmail("loginuser@test.com");
        login.setPassword("Password@1");

        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").value(greaterThan(0)))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("accessToken");
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with wrong password → 401")
    void login_wrongPassword_shouldReturn401() throws Exception {
        // Register first
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail("wrongpass@test.com");
        reg.setPassword("Password@1");
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(reg)));

        LoginRequest login = new LoginRequest();
        login.setEmail("wrongpass@test.com");
        login.setPassword("WrongPassword");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(login)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with unknown email → 401")
    void login_unknownUser_shouldReturn401() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail("nobody@test.com");
        login.setPassword("Password@1");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(login)))
            .andExpect(status().isUnauthorized());
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/auth/refresh with valid token → 200 new tokens")
    void refresh_shouldReturnNewTokens() throws Exception {
        // Register + login
        RegisterRequest reg = new RegisterRequest();
        reg.setEmail("refresh@test.com");
        reg.setPassword("Password@1");
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(reg)));

        LoginRequest login = new LoginRequest();
        login.setEmail("refresh@test.com");
        login.setPassword("Password@1");
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(login)))
            .andReturn();

        String refreshToken = om.readTree(loginResult.getResponse().getContentAsString())
            .at("/data/refreshToken").asText();

        // Refresh
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh with invalid token → 401")
    void refresh_invalidToken_shouldReturn401() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"invalid-token\"}"))
            .andExpect(status().isUnauthorized());
    }
}
