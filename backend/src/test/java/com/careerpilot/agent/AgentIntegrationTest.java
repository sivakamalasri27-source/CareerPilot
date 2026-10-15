package com.careerpilot.agent;

import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.agent.AuditRequest;
import com.careerpilot.dto.agent.RecruiterSimulationRequest;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.EducationRequest;
import com.careerpilot.dto.profile.ProfileRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.entity.AuditReport;
import com.careerpilot.repository.AuditReportRepository;
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
import java.util.List;
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
class AgentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private AuditReportRepository auditReportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    private String userAToken;
    private Long userAId;

    @BeforeEach
    void setUp() throws Exception {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        // 1. Register User A and build complete profile
        String emailA = "user_a_" + UUID.randomUUID() + "@college.edu";
        userAToken = registerAndGetToken(emailA, "User Alpha");
        userAId = userRepository.findByEmail(emailA).orElseThrow().getId();

        // Populate Profile for User A
        ProfileRequest profileReq = ProfileRequest.builder()
                .headline("Junior Java Backend Developer | Spring Boot")
                .about("Computer science student with hands-on project experience building backend systems.")
                .targetRole("Backend Engineer")
                .build();
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk());

        // Add Skill
        ProfileSkillRequest skillReq = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("INTERMEDIATE")
                .build();
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated());

        // Add Project
        ProjectRequest projectReq = ProjectRequest.builder()
                .title("CareerPilot Microservices")
                .description("Production-grade profile coach backend")
                .technologies("Java 17, Spring Boot 3, PostgreSQL")
                .repoUrl("https://github.com/careerpilot/backend")
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 6, 1))
                .bullets(List.of("Implemented JWT auth with refresh rotation", "Integrated multi-agent AI pipeline"))
                .build();
        mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated());

        // Add Education
        EducationRequest eduReq = EducationRequest.builder()
                .institution("State University")
                .degree("Bachelor of Technology")
                .fieldOfStudy("Computer Science")
                .gradeOrGpa("3.9")
                .startDate(LocalDate.of(2021, 8, 1))
                .endDate(LocalDate.of(2025, 5, 1))
                .build();
        mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Orchestrator: Full audit runs ProfileAuditor and RecruiterSimulator in parallel and persists report")
    void testFullAuditSuccess() throws Exception {
        AuditRequest request = AuditRequest.builder()
                .targetRole("Backend Engineer")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.requestType").value("FULL_AUDIT"))
                .andExpect(jsonPath("$.auditorResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.auditorResult.data.overallScore").value(85))
                .andExpect(jsonPath("$.recruiterResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recruiterResult.data.personas").isArray())
                .andExpect(jsonPath("$.recruiterResult.data.consensusDecision").value("YES"))
                .andReturn();

        // Verify report persisted in audit_reports table
        Long reportId = objectMapper.readTree(result.getResponse().getContentAsString()).path("reportId").asLong();
        AuditReport report = auditReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getOverallScore()).isEqualTo(85);
        assertThat(report.getSectionScores()).isNotEmpty();
        assertThat(report.getWeaknesses()).isNotEmpty();
        assertThat(report.getRecruiterFeedback()).isNotNull();
    }

    @Test
    @DisplayName("Orchestrator: Recruiter simulation returns 3 personas and consensus decision")
    void testRecruiterSimulationSuccess() throws Exception {
        RecruiterSimulationRequest request = RecruiterSimulationRequest.builder()
                .targetRole("Backend Engineer")
                .build();

        mockMvc.perform(post("/api/v1/agents/recruiter-simulation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestType").value("RECRUITER_VIEW"))
                .andExpect(jsonPath("$.recruiterResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recruiterResult.data.personas").isArray())
                .andExpect(jsonPath("$.recruiterResult.data.personas.length()").value(3))
                .andExpect(jsonPath("$.recruiterResult.data.consensusDecision").value("YES"));
    }

    @Test
    @DisplayName("Orchestrator: Tolerates partial failure when one agent falls back or fails")
    void testPartialFailureHandling() throws Exception {
        fakeLlmClient.queueResponseForAgent("RecruiterSimulatorAgent", "{ malformed recruiter json");
        fakeLlmClient.queueResponseForAgent("RecruiterSimulatorAgent", "{ malformed recruiter repair 1");
        fakeLlmClient.queueResponseForAgent("RecruiterSimulatorAgent", "{ malformed recruiter repair 2");

        AuditRequest request = AuditRequest.builder()
                .targetRole("Backend Engineer")
                .build();

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.auditorResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.recruiterResult.status").value("FALLBACK"));
    }

    @Test
    @DisplayName("Validation: Empty or near-empty profile is rejected with HTTP 400 without calling LLM")
    void testEmptyProfileReturns400() throws Exception {
        // Register User with empty profile (no headline, no skills, no projects, no education)
        String emptyUserEmail = "empty_user_" + UUID.randomUUID() + "@college.edu";
        String emptyUserToken = registerAndGetToken(emptyUserEmail, "Empty User");

        fakeLlmClient.reset();

        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emptyUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Profile is incomplete or empty")));

        // Verify zero LLM calls were made!
        assertThat(fakeLlmClient.getRecordedRequests()).isEmpty();
    }

    @Test
    @DisplayName("Ownership: User A cannot read User B's audit report (returns 404)")
    void testOwnershipEnforcement() throws Exception {
        // User A generates an audit report
        MvcResult auditResult = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        Long reportId = objectMapper.readTree(auditResult.getResponse().getContentAsString()).path("reportId").asLong();

        // User B registers and attempts to access User A's report
        String userBToken = registerAndGetToken("user_b_" + UUID.randomUUID() + "@college.edu", "User Beta");

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // User A can access their own report
        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId));
    }

    @Test
    @DisplayName("Reports: Paginated list returns user's audit reports")
    void testPaginatedReports() throws Exception {
        // Run audit for User A
        mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].overallScore").value(85));
    }

    @Test
    @DisplayName("Security: Unauthenticated access returns HTTP 401")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/agents/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/agents/recruiter-simulation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reports"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("StrongPass123!")
                .fullName(fullName)
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getAccessToken();
    }
}
