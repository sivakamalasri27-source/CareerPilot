package com.careerpilot.ai;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.ai.dto.SampleAuditDto;
import com.careerpilot.ai.ratelimit.RateLimiterService;
import com.careerpilot.ai.service.AiExecutionService;
import com.careerpilot.ai.service.TokenUsageTracker;
import com.careerpilot.dto.ai.TestAiAuditRequest;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.entity.AiUsageLog;
import com.careerpilot.entity.User;
import com.careerpilot.repository.AiUsageLogRepository;
import com.careerpilot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private AiExecutionService aiExecutionService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private TokenUsageTracker tokenUsageTracker;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private Cache<String, Object> aiResultCache;

    private String authToken;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
        rateLimiterService.clearAll();
        circuitBreakerRegistry.circuitBreaker("llmService").reset();

        String uniqueEmail = "ai_test_" + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("StrongPass123!")
                .fullName("AI Tester")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);
        authToken = authResponse.getAccessToken();
        testUser = userRepository.findByEmail(uniqueEmail).orElseThrow();
    }

    @Test
    @DisplayName("AI Layer: Valid JSON response parsed, validated, cached, and logged")
    void testValidJsonExecution() throws Exception {
        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Backend Engineer")
                .candidateData("Completed Java Spring Boot course and built REST API.")
                .build();

        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.summary").isNotEmpty())
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.missingFields").isArray());

        // Verify ai_usage_logs
        List<AiUsageLog> logs = aiUsageLogRepository.findAllByUserId(testUser.getId());
        assertThat(logs).hasSize(1);
        AiUsageLog log = logs.get(0);
        assertThat(log.getIsSuccess()).isTrue();
        assertThat(log.getPromptTokens()).isGreaterThan(0);
        assertThat(log.getCompletionTokens()).isGreaterThan(0);
        assertThat(log.getTotalTokens()).isGreaterThan(0);
        assertThat(log.getEstimatedCostUsd()).isGreaterThan(BigDecimal.ZERO);
        assertThat(log.getAgentName()).isEqualTo("TestAuditor");
    }

    @Test
    @DisplayName("AI Layer: Malformed JSON triggers repair prompt and succeeds on retry")
    void testMalformedJsonRepaired() throws Exception {
        // Attempt 1: Return JSON with invalid field type
        fakeLlmClient.queueResponse("{\"score\": \"not-a-number\", \"summary\": \"Invalid score type\"}");
        // Attempt 2 (repair): Return strictly valid JSON
        fakeLlmClient.queueResponse("""
                {
                  "score": 92,
                  "summary": "Repaired output after validation feedback.",
                  "recommendations": ["Add unit tests"],
                  "missingFields": ["Portfolio link"]
                }
                """);

        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Fullstack Developer")
                .candidateData("React frontend and Node backend projects.")
                .build();

        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(92))
                .andExpect(jsonPath("$.summary").value("Repaired output after validation feedback."));

        // LLM called twice: initial + 1 repair
        assertThat(fakeLlmClient.getRecordedRequests()).hasSize(2);
        // Verify repair prompt was used
        assertThat(fakeLlmClient.getRecordedRequests().get(1).getPrompt()).contains("repair agent");
    }

    @Test
    @DisplayName("AI Layer: Exhausted repair attempts returns graceful fallback (never 500)")
    void testRepairExhaustedReturnsFallback() throws Exception {
        // Always return unparseable JSON
        fakeLlmClient.setDefaultResponse("{ this is not json at all }}}");

        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Data Scientist")
                .candidateData("Python, pandas, numpy skills.")
                .build();

        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()) // Never a 500!
                .andExpect(jsonPath("$.score").value(50)) // Fallback score
                .andExpect(jsonPath("$.summary").value("Fallback audit generated due to AI processing constraints."));

        // 1 initial + 2 repair attempts = 3 total calls
        assertThat(fakeLlmClient.getRecordedRequests()).hasSize(3);

        // Verify usage log marked with isSuccess = false
        List<AiUsageLog> logs = aiUsageLogRepository.findAllByUserId(testUser.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getIsSuccess()).isFalse();
    }

    @Test
    @DisplayName("AI Layer: LLM Timeout triggers transient retry and circuit breaker recording")
    void testTimeoutHandling() {
        fakeLlmClient.setSimulateTimeout(true);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmService");
        int initialFailedCalls = circuitBreaker.getMetrics().getNumberOfFailedCalls();

        assertThatThrownBy(() -> aiExecutionService.execute(
                testUser,
                "TestAuditor",
                "sample-audit.v1.txt",
                Map.of("targetRole", "DevOps Engineer"),
                "Docker and CI/CD pipelines",
                SampleAuditDto.class,
                null
        )).isInstanceOf(ResourceAccessException.class);

        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isGreaterThan(initialFailedCalls);
    }

    @Test
    @DisplayName("AI Layer: Circuit breaker opens on consecutive failures and returns 503")
    void testCircuitBreakerOpening() throws Exception {
        fakeLlmClient.setSimulateServerError(true);

        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Mobile Engineer")
                .candidateData("Flutter and Android app")
                .build();

        // Minimum calls configured in application-test.yml is 2 with 50% threshold
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/test/ai/execute")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llmService");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When OPEN, call must fail fast and return 503 Service Unavailable
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service is temporarily unavailable due to high failure rate. Please try again later."));
    }

    @Test
    @DisplayName("AI Layer: Rate limiter with Bucket4j returns 429 with Retry-After header")
    void testRateLimitExceededReturns429() throws Exception {
        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Cloud Architect")
                .candidateData("AWS and Kubernetes")
                .build();

        // In application-test.yml, capacity is 3
        // 1st request -> OK
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Invalidate cache so subsequent requests consume tokens rather than returning cached result
        aiResultCache.invalidateAll();

        // 2nd request -> OK
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        aiResultCache.invalidateAll();

        // 3rd request -> OK
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        aiResultCache.invalidateAll();

        // 4th request -> Rate limit exceeded! (Capacity is 3)
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("AI rate limit exceeded. Please wait before making more requests."));
    }

    @Test
    @DisplayName("AI Layer: Caffeine cache returns cached result and avoids second LLM call")
    void testCacheHitAvoidsSecondCall() throws Exception {
        TestAiAuditRequest request = TestAiAuditRequest.builder()
                .targetRole("Security Analyst")
                .candidateData("Network security and cryptography coursework.")
                .build();

        // First call: calls LLM
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(fakeLlmClient.getRecordedRequests()).hasSize(1);

        // Second call with identical input: should hit cache!
        mockMvc.perform(post("/api/v1/test/ai/execute")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // LLM was NOT invoked a second time!
        assertThat(fakeLlmClient.getRecordedRequests()).hasSize(1);
    }
}
