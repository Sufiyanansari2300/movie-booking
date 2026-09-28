package com.sufiyan.moviebooking.controller;

import com.sufiyan.moviebooking.config.JwtProperties;
import com.sufiyan.moviebooking.entity.Role;
import com.sufiyan.moviebooking.entity.User;
import com.sufiyan.moviebooking.security.AppUserPrincipal;
import com.sufiyan.moviebooking.security.JwtTokenService;
import com.sufiyan.moviebooking.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.sufiyan.moviebooking.support.AuthTestSupport.bearer;
import static com.sufiyan.moviebooking.support.AuthTestSupport.login;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    // --- registration ---

    @Test
    void register_createsCustomer() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob", "email": "Bob@Example.com", "password": "password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        userService.createUser("Bob", "bob@example.com", "password123", Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Bob Again", "email": "BOB@example.com", "password": "password123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void register_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("password")));
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    // --- login ---

    @Test
    void login_returnsBearerTokenValidForOneDay() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Carol@Example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.user.email").value("carol@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "carol@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_unknownEmail_returnsSameErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody@example.com", "password": "password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    // --- using the token ---

    @Test
    void me_withValidToken_returnsCurrentUser() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);
        String token = login(mockMvc, "carol@example.com", "password123");

        mockMvc.perform(get("/api/auth/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carol@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void me_withGarbageToken_returns401InvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void me_withTamperedToken_returns401InvalidToken() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);
        String token = login(mockMvc, "carol@example.com", "password123");
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        mockMvc.perform(get("/api/auth/me").with(bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void me_withExpiredToken_returns401InvalidToken() throws Exception {
        User carol = userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);
        // Issued two days ago with a one-day lifetime.
        Clock twoDaysAgo = Clock.fixed(Instant.now().minus(Duration.ofDays(2)), ZoneOffset.UTC);
        String expired = new JwtTokenService(jwtEncoder, jwtProperties, twoDaysAgo)
                .issue(AppUserPrincipal.from(carol)).value();

        mockMvc.perform(get("/api/auth/me").with(bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void basicAuth_isNoLongerAccepted() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);

        mockMvc.perform(get("/api/auth/me").with(httpBasic("carol@example.com", "password123")))
                .andExpect(status().isUnauthorized());
    }

    // --- roles ---

    @Test
    void customer_cannotAccessAdminApi() throws Exception {
        userService.createUser("Carol", "carol@example.com", "password123", Role.CUSTOMER);
        String token = login(mockMvc, "carol@example.com", "password123");

        mockMvc.perform(get("/api/admin/anything").with(bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void admin_passesAuthorizationForAdminApi() throws Exception {
        userService.createUser("Root", "root@example.com", "password123", Role.ADMIN);
        String token = login(mockMvc, "root@example.com", "password123");

        // No admin endpoints exist yet: getting past security yields the JSON 404, not 401/403.
        mockMvc.perform(get("/api/admin/anything").with(bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
