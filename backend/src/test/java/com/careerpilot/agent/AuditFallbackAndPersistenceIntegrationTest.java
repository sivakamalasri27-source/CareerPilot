package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.agent.AuditRequest;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.EducationRequest;
import com.careerpilot.dto.profile.ProfileRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.entity.AuditReport;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.repository.AuditReportRepository;
import com.careerpilot.repository.ProfileRepository;
import com.careerpilot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditFallbackAndPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private AuditReportRepository auditReportRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @Autowired
    private com.careerpilot.ai.ratelimit.RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
        rateLimiterService.clearAll();
    }

    private String createPopulatedProfile() throws Exception {
        String email = "test_" + UUID.randomUUID() + "@university.edu";
        RegisterRequest registerReq = RegisterRequest.builder()
                .fullName("Alex Candidate")
                .email(email)
                .password("Password123!")
                .build();

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);
        String token = auth.getAccessToken();

        ProfileRequest profileReq = ProfileRequest.builder()
                .headline("Java Backend Developer | Spring Boot & Microservices")
                .about("Computer science student with practical experience building distributed backend services and scalable RESTful APIs.")
                .targetRole("Software Engineer")
                .build();
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk());

        ProjectRequest projectReq = ProjectRequest.builder()
                .title("Distributed Task Queue")
                .description("Built resilient message queue with Spring Boot and PostgreSQL")
                .technologies("Java, Spring Boot, PostgreSQL, Docker")
                .startDate(LocalDate.of(2025, 1, 1))
                .bullets(List.of("Engineered worker pool processing 500 tasks/second", "Configured Docker container deployment"))
                .build();
        mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated());

        ProfileSkillRequest skillReq = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("INTERMEDIATE")
                .build();
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated());

        EducationRequest eduReq = EducationRequest.builder()
                .institution("State University")
                .degree("Bachelor of Science")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2021, 9, 1))
                .build();
        mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduReq)))
                .andExpect(status().isCreated());

        return token;
    }

    @Test
    @DisplayName("When AI returns 401, audit returns 503 and report is NOT persisted")
    void whenAiReturns401_shouldReturn503AndNotPersistAuditReport() throws Exception {
        String token = createPopulatedProfile();
        long initialReportCount = auditReportRepository.count();

        fakeLlmClient.setSimulateStatusCode(401);

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service unavailable: Gemini rejected the API key. Please retry."));

        assertThat(auditReportRepository.count()).isEqualTo(initialReportCount);
    }

    @Test
    @DisplayName("When AI returns 403, audit returns 503 and report is NOT persisted")
    void whenAiReturns403_shouldReturn503AndNotPersistAuditReport() throws Exception {
        String token = createPopulatedProfile();
        long initialReportCount = auditReportRepository.count();

        fakeLlmClient.setSimulateStatusCode(403);

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service unavailable: Gemini rejected the API key. Please retry."));

        assertThat(auditReportRepository.count()).isEqualTo(initialReportCount);
    }

    @Test
    @DisplayName("When AI returns 404, audit returns 503 with model not found and report is NOT persisted")
    void whenAiReturns404_shouldReturn503AndNotPersistAuditReport() throws Exception {
        String token = createPopulatedProfile();
        long initialReportCount = auditReportRepository.count();

        fakeLlmClient.setSimulateStatusCode(404);

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service unavailable: Model 'gemini-test' not found. Available models: [models/gemini-2.5-flash]. Please retry."));

        assertThat(auditReportRepository.count()).isEqualTo(initialReportCount);
    }

    @Test
    @DisplayName("When AI returns 429, audit returns 503 with quota exceeded and report is NOT persisted")
    void whenAiReturns429_shouldReturn503AndNotPersistAuditReport() throws Exception {
        String token = createPopulatedProfile();
        long initialReportCount = auditReportRepository.count();

        fakeLlmClient.setSimulateStatusCode(429);

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service unavailable: Gemini API quota exceeded (HTTP 429). Please retry."));

        assertThat(auditReportRepository.count()).isEqualTo(initialReportCount);
    }

    @Test
    @DisplayName("Unchanged check ignores failed reports (overall 50, all zero) and compares only to successful audit")
    void unchangedCheckIgnoresFailedReports() throws Exception {
        String token = createPopulatedProfile();

        User user = userRepository.findAll().get(userRepository.findAll().size() - 1);
        Profile profile = profileRepository.findByUserId(user.getId()).orElseThrow();

        // Seed a dummy failed report in database (overall 50, empty section scores)
        AuditReport failedDummy = AuditReport.builder()
                .profile(profile)
                .overallScore(50)
                .sectionScores(new HashMap<>())
                .weaknesses(List.of())
                .profileContentHash("any_dummy_hash")
                .requestType("FULL_AUDIT")
                .build();
        auditReportRepository.save(failedDummy);

        // 1. Run audit with normal AI response - should NOT match failed report as unchanged
        fakeLlmClient.setDefaultResponse(FakeLlmClient.CANNED_AUDITOR_RESPONSE);
        rateLimiterService.clearAll();
        MvcResult firstSuccess = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(false))
                .andExpect(jsonPath("$.auditorResult.data.overallScore").value(85))
                .andReturn();

        // 2. Run audit again without profile changes - should now detect unchanged against the successful report
        rateLimiterService.clearAll();
        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(true))
                .andExpect(jsonPath("$.auditorResult.data.overallScore").value(85));

        // 3. Run audit with force=true - should re-run anyway
        rateLimiterService.clearAll();
        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AuditRequest.builder().force(true).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(false));
    }

    @Test
    @DisplayName("AI Health endpoint returns UP when client is healthy and DOWN when failed")
    void testAiHealthEndpoint() throws Exception {
        String token = createPopulatedProfile();

        // Healthy probe
        mockMvc.perform(get("/api/v1/ai/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("OK"));

        // Simulated error probe
        fakeLlmClient.setSimulateStatusCode(401);
        mockMvc.perform(get("/api/v1/ai/health")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.error").value("Gemini rejected the API key"));
    }
}
