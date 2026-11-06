package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.agent.TranslateExperienceRequest;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.dto.profile.ProjectResponse;
import com.careerpilot.agent.dto.ParsedProjectDto;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.dto.suggestion.SuggestionResponse;
import com.careerpilot.entity.*;
import com.careerpilot.repository.AgentSuggestionRepository;
import com.careerpilot.repository.ProjectRepository;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuggestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private AgentSuggestionRepository agentSuggestionRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.careerpilot.repository.ProfileRepository profileRepository;

    @Autowired
    private com.careerpilot.repository.ProfileSkillRepository profileSkillRepository;

    @Autowired
    private com.careerpilot.repository.SkillRepository skillRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
    }

    private String registerAndGetToken(String emailPrefix) throws Exception {
        String email = emailPrefix + "." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Career Tester")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getAccessToken();
    }

    private Long createSampleProject(String token) throws Exception {
        ProjectRequest projectRequest = ProjectRequest.builder()
                .title("Inventory Management Service")
                .description("Distributed inventory tracker built using Spring Boot and Postgres")
                .technologies("Java, Spring Boot, PostgreSQL, Docker")
                .bullets(List.of("Designed REST endpoints for stock management", "Added basic integration tests"))
                .isAcademic(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        ProjectResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ProjectResponse.class);
        return response.getId();
    }

    @Test
    @DisplayName("Translate Experience: Valid project translates into bullets and creates PENDING suggestion")
    void testTranslateExperienceValidOutput() throws Exception {
        String token = registerAndGetToken("translator.valid");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .targetRole("Backend Engineer")
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentResult.status").value("SUCCESS"))
                .andExpect(jsonPath("$.agentResult.data.bullets").isArray())
                .andExpect(jsonPath("$.agentResult.data.clarifyingQuestions").isArray())
                .andExpect(jsonPath("$.suggestion.status").value("PENDING"))
                .andExpect(jsonPath("$.suggestion.targetSection").value("PROJECT_BULLETS"))
                .andExpect(jsonPath("$.suggestion.targetId").value(projectId))
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        AgentSuggestion suggestion = agentSuggestionRepository.findById(suggestionId).orElseThrow();
        assertThat(suggestion.getStatus()).isEqualTo(SuggestionStatus.PENDING);
        assertThat(suggestion.getOriginalTextHash()).isNotBlank();
        assertThat(suggestion.getSuggestedText()).contains("Spring Boot");
    }

    @Test
    @DisplayName("Approve Flow: Applies suggested bullets to target project and marks APPROVED")
    void testApproveSuggestionAppliesChange() throws Exception {
        String token = registerAndGetToken("approve.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Approve the suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // Verify database state: project bullets updated
        Project updatedProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(updatedProject.getBullets()).isNotEmpty();
        assertThat(updatedProject.getBullets().get(0)).contains("Spring Boot");
    }

    @Test
    @DisplayName("Approve Flow: Optional editedText validates and replaces suggested text")
    void testApproveWithEditedText() throws Exception {
        String token = registerAndGetToken("approve.edited");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        ApproveSuggestionRequest approveRequest = ApproveSuggestionRequest.builder()
                .editedText("[\"Custom engineered resilient API with 99.9% uptime\", \"Integrated Redis cache layer reducing query latency\"]")
                .build();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Project updatedProject = projectRepository.findById(projectId).orElseThrow();
        assertThat(updatedProject.getBullets()).containsExactly(
                "Custom engineered resilient API with 99.9% uptime",
                "Integrated Redis cache layer reducing query latency"
        );
    }

    @Test
    @DisplayName("Reject Flow: Marks suggestion REJECTED and leaves project unchanged")
    void testRejectSuggestionChangesNothing() throws Exception {
        String token = registerAndGetToken("reject.flow");
        Long projectId = createSampleProject(token);

        List<String> originalBullets = projectRepository.findById(projectId).orElseThrow().getBullets();

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Reject the suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        // Project bullets should remain exactly as originally saved
        Project projectAfterReject = projectRepository.findById(projectId).orElseThrow();
        assertThat(projectAfterReject.getBullets()).isEqualTo(originalBullets);
    }

    @Test
    @DisplayName("Stale Check: Modifying project after suggestion creation returns 409 Conflict")
    void testStaleSuggestionReturns409() throws Exception {
        String token = registerAndGetToken("stale.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // Modify project directly so the target content changes (hash mismatch)
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.setBullets(List.of("Completely changed bullets by concurrent update"));
        projectRepository.save(project);

        // Attempting to approve must return 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("stale")));
    }

    @Test
    @DisplayName("Double Decision: Deciding an already APPROVED or REJECTED suggestion returns 409")
    void testDoubleDecisionReturns409() throws Exception {
        String token = registerAndGetToken("double.decision");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // First decision: approve
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Second decision attempt (approve again): 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been decided")));

        // Reject attempt on already approved suggestion: 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already been decided")));
    }

    @Test
    @DisplayName("Ownership Isolation: User A cannot see or decide User B's suggestion (returns 404)")
    void testCrossUserAccessReturns404() throws Exception {
        String tokenA = registerAndGetToken("userA.flow");
        String tokenB = registerAndGetToken("userB.flow");

        Long projectIdA = createSampleProject(tokenA);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectIdA)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionIdA = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // User B tries to view User A's suggestion
        mockMvc.perform(get("/api/v1/suggestions/" + suggestionIdA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B tries to approve User A's suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionIdA + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // User B tries to reject User A's suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionIdA + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Security: Unauthenticated calls return 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/suggestions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/suggestions/1/approve"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/suggestions/1/reject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Pagination & Filtering: GET /suggestions returns newest first with status filter")
    void testPaginationAndFiltering() throws Exception {
        String token = registerAndGetToken("pagination.flow");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest req = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        // Create 2 suggestions
        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Fetch paginated
        mockMvc.perform(get("/api/v1/suggestions?status=PENDING&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("Resume Project Import: Approving creates new project with title, description, role, tech, and bullets")
    void testApproveResumeProjectCreatesProject() throws Exception {
        String email = "resume.proj." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Career Tester")
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        User user = userRepository.findByEmail(email).orElseThrow();
        Profile profile = profileRepository.findByUserId(user.getId()).orElseThrow();

        ParsedProjectDto projectDto = ParsedProjectDto.builder()
                .title("Real-Time Analytics Pipeline")
                .description("Streaming data engine built with Flink and Kafka")
                .technologies("Java, Apache Kafka, Apache Flink")
                .role("Data Engineer")
                .bullets(List.of("Processed 1M events/sec with sub-second latency", "Engineered fault-tolerant state checkpointing"))
                .build();

        AgentSuggestion suggestion = AgentSuggestion.builder()
                .profile(profile)
                .agentName("ResumeParserAgent")
                .targetSection("PROJECT")
                .targetId(null)
                .originalText("")
                .originalTextHash("")
                .suggestedText(objectMapper.writeValueAsString(projectDto))
                .rationale("Imported project from resume")
                .status(SuggestionStatus.PENDING)
                .build();
        suggestion = agentSuggestionRepository.save(suggestion);

        // Approve the suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify that the project was created in the database
        List<Project> projects = projectRepository.findAllByProfileId(profile.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertThat(projects).hasSize(1);
        Project created = projects.get(0);
        assertThat(created.getTitle()).isEqualTo("Real-Time Analytics Pipeline");
        assertThat(created.getDescription()).isEqualTo("Streaming data engine built with Flink and Kafka");
        assertThat(created.getTechnologies()).isEqualTo("Java, Apache Kafka, Apache Flink");
        assertThat(created.getRole()).isEqualTo("Data Engineer");
        assertThat(created.getBullets()).containsExactly(
                "Processed 1M events/sec with sub-second latency",
                "Engineered fault-tolerant state checkpointing"
        );
    }

    @Test
    @DisplayName("Resume Skill Import: Approving skips duplicate if skill already exists on profile")
    void testApproveResumeSkillDuplicateSkipped() throws Exception {
        String email = "resume.skill." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Career Tester")
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        User user = userRepository.findByEmail(email).orElseThrow();
        Profile profile = profileRepository.findByUserId(user.getId()).orElseThrow();

        // Create initial Java skill if not already present
        Skill skill = skillRepository.findByNameIgnoreCase("Java")
                .orElseGet(() -> skillRepository.save(Skill.builder().name("Java").category("TECHNICAL").build()));
        if (!profileSkillRepository.existsByProfileIdAndSkillNameIgnoreCase(profile.getId(), "Java")) {
            profileSkillRepository.save(ProfileSkill.builder()
                    .profile(profile)
                    .skill(skill)
                    .claimedLevel("INTERMEDIATE")
                    .verificationStatus(VerificationStatus.UNVERIFIED)
                    .build());
        }

        // Suggestion to add Java (duplicate)
        AgentSuggestion suggestion = AgentSuggestion.builder()
                .profile(profile)
                .agentName("ResumeParserAgent")
                .targetSection("SKILL")
                .targetId(null)
                .originalText("")
                .originalTextHash("")
                .suggestedText("Java (ADVANCED)")
                .rationale("Imported skill from resume")
                .status(SuggestionStatus.PENDING)
                .build();
        suggestion = agentSuggestionRepository.save(suggestion);

        // Approve suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Verify count of skills is still 1
        long skillCount = profileSkillRepository.countByProfileId(profile.getId());
        assertThat(skillCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Validation: Malformed JSON suggestion payload returns 400 Bad Request")
    void testApproveMalformedJsonReturns400() throws Exception {
        String email = "malformed.json." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Career Tester")
                .build();
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readValue(res.getResponse().getContentAsString(), AuthResponse.class).getAccessToken();

        User user = userRepository.findByEmail(email).orElseThrow();
        Profile profile = profileRepository.findByUserId(user.getId()).orElseThrow();

        AgentSuggestion suggestion = AgentSuggestion.builder()
                .profile(profile)
                .agentName("ResumeParserAgent")
                .targetSection("PROJECT")
                .targetId(null)
                .originalText("")
                .originalTextHash("")
                .suggestedText("{not a valid json string: broken}")
                .rationale("Imported project with malformed JSON")
                .status(SuggestionStatus.PENDING)
                .build();
        suggestion = agentSuggestionRepository.save(suggestion);

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestion.getId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed project suggestion JSON payload"));
    }

    @Test
    @DisplayName("Security: User B cannot approve User A suggestion and receives 404 Not Found")
    void testUserBCannotApproveUserASuggestionReturns404() throws Exception {
        String tokenA = registerAndGetToken("userA.approve");
        String tokenB = registerAndGetToken("userB.approve");
        Long projectIdA = createSampleProject(tokenA);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectIdA)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionIdA = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // User B attempts to approve User A's suggestion
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionIdA + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Conflict: Double approve on already decided suggestion returns 409 Conflict")
    void testDoubleApproveReturns409() throws Exception {
        String token = registerAndGetToken("double.approve");
        Long projectId = createSampleProject(token);

        TranslateExperienceRequest request = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long suggestionId = objectMapper.readTree(mvcResult.getResponse().getContentAsString())
                .path("suggestion").path("id").asLong();

        // First approval: succeeds
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Second approval: returns 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Suggestion has already been decided")));
    }
}
