package com.careerpilot.controller;

import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.LoginRequest;
import com.careerpilot.dto.auth.RefreshTokenRequest;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.repository.RefreshTokenRepository;
import com.careerpilot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully register a new user and return JWT tokens")
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("alex.smith@college.edu")
                .fullName("Alex Smith")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("alex.smith@college.edu"))
                .andExpect(jsonPath("$.user.fullName").value("Alex Smith"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    @DisplayName("Should successfully register when JSON body uses fullName field")
    void shouldRegisterUserSuccessfullyWithFilledFullName() throws Exception {
        String json = """
                {
                  "fullName": "Jane Doe",
                  "email": "jane.doe@college.edu",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.user.email").value("jane.doe@college.edu"));
    }

    @Test
    @DisplayName("Should successfully register when JSON body uses 'name' alias")
    void shouldRegisterUserSuccessfullyWhenJsonUsesNameAlias() throws Exception {
        String json = """
                {
                  "name": "Alias User",
                  "email": "alias.user@college.edu",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.fullName").value("Alias User"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when fullName is blank")
    void shouldFailRegistrationWhenFullNameIsBlank() throws Exception {
        String json = """
                {
                  "fullName": "   ",
                  "email": "blank.name@college.edu",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.fullName").value("Full name is required"));
    }

    @Test
    @DisplayName("Should return 409 Conflict when registering with duplicate email")
    void shouldFailRegistrationWhenEmailIsDuplicate() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("duplicate@college.edu")
                .fullName("Original User")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        RegisterRequest duplicateRequest = RegisterRequest.builder()
                .email("duplicate@college.edu")
                .fullName("Duplicate Candidate")
                .password("AnotherPass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("Email is already registered")));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when password does not meet security rules")
    void shouldFailRegistrationWhenPasswordWeak() throws Exception {
        RegisterRequest weakPasswordRequest = RegisterRequest.builder()
                .email("weak@college.edu")
                .fullName("Weak Password User")
                .password("123") // too short (< 6 chars)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakPasswordRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void shouldLoginSuccessfullyWithCorrectCredentials() throws Exception {
        registerTestUser("login.test@college.edu", "Password123!");

        LoginRequest loginRequest = LoginRequest.builder()
                .email("login.test@college.edu")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login.test@college.edu"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when logging in with incorrect password")
    void shouldFailLoginWithWrongPassword() throws Exception {
        registerTestUser("wrong.pass@college.edu", "Password123!");

        LoginRequest loginRequest = LoginRequest.builder()
                .email("wrong.pass@college.edu")
                .password("IncorrectPassword456!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Should allow access to protected /api/v1/auth/me endpoint with valid Bearer token")
    void shouldAccessProtectedEndpointWithValidToken() throws Exception {
        AuthResponse authResponse = registerTestUser("me.test@college.edu", "Password123!");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + authResponse.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me.test@college.edu"))
                .andExpect(jsonPath("$.fullName").value("Test Candidate"));
    }

    @Test
    @DisplayName("Should reject access to protected endpoint without token with 401 Unauthorized")
    void shouldRejectAccessToProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Should reject access to protected endpoint with tampered token")
    void shouldRejectAccessWithTamperedOrMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.tampered.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Should rotate refresh token and issue new access token upon refresh")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        AuthResponse authResponse = registerTestUser("rotate.test@college.edu", "Password123!");
        String initialRefreshToken = authResponse.getRefreshToken();

        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(initialRefreshToken)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        AuthResponse newAuth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(newAuth.getRefreshToken()).isNotEqualTo(initialRefreshToken);
    }

    @Test
    @DisplayName("Should trigger reuse detection when revoked refresh token is presented and revoke all sessions")
    void shouldTriggerReuseDetectionAndRevokeAllTokensWhenReused() throws Exception {
        // Step 1: User registers and gets Token A
        AuthResponse authA = registerTestUser("reuse.test@college.edu", "Password123!");
        String tokenA = authA.getRefreshToken();

        // Step 2: User rotates Token A -> receives Token B (Token A is now revoked)
        RefreshTokenRequest rotateRequest = RefreshTokenRequest.builder()
                .refreshToken(tokenA)
                .build();

        MvcResult rotateResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authB = objectMapper.readValue(rotateResult.getResponse().getContentAsString(), AuthResponse.class);
        String tokenB = authB.getRefreshToken();

        // Step 3: Malicious replay / reuse: Presenting the already-revoked Token A again!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rotateRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Refresh token reuse detected")));

        // Step 4: Verify that Token B was ALSO revoked due to reuse detection!
        RefreshTokenRequest attemptTokenB = RefreshTokenRequest.builder()
                .refreshToken(tokenB)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attemptTokenB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Should successfully logout and revoke the presented refresh token")
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
        AuthResponse auth = registerTestUser("logout.test@college.edu", "Password123!");

        RefreshTokenRequest logoutRequest = RefreshTokenRequest.builder()
                .refreshToken(auth.getRefreshToken())
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // Subsequent refresh attempt with logged out token must fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized());
    }

    private AuthResponse registerTestUser(String email, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .fullName("Test Candidate")
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }
}
