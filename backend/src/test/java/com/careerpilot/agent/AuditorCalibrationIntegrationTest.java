package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.ai.service.AiExecutionService;
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
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditorCalibrationIntegrationTest {

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

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
    }

    @Test
    @DisplayName("Calibration: Weak profile scores low according to strict rubric")
    void testWeakProfileScoresLow() throws Exception {
        String email = "weak_" + UUID.randomUUID() + "@college.edu";
        String token = registerAndGetToken(email, "Weak Candidate");

        // Weak headline (<40 chars, generic "Aspiring developer") and weak about (<100 chars)
        ProfileRequest profileReq = ProfileRequest.builder()
                .headline("Aspiring developer")
                .about("I love coding and want a job.")
                .targetRole("Software Engineer")
                .build();
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk());

        // Weak project (no tech stack, no bullets, no measurable outcome)
        ProjectRequest projectReq = ProjectRequest.builder()
                .title("Simple App")
                .description("A basic web page")
                .startDate(LocalDate.of(2025, 1, 1))
                .build();
        mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated());

        // Basic skill & education
        ProfileSkillRequest skillReq = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("BEGINNER")
                .build();
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated());

        EducationRequest eduReq = EducationRequest.builder()
                .institution("City College")
                .degree("B.S.")
                .fieldOfStudy("Computer Science")
                .startDate(LocalDate.of(2022, 1, 1))
                .endDate(LocalDate.of(2026, 1, 1))
                .build();
        mockMvc.perform(post("/api/v1/profile/education")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduReq)))
                .andExpect(status().isCreated());

        // Execute audit
        MvcResult result = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode auditorData = json.path("auditorResult").path("data");

        int overallScore = auditorData.path("overallScore").asInt();
        int headlineScore = auditorData.path("sectionScores").path("headline").asInt();
        int aboutScore = auditorData.path("sectionScores").path("about").asInt();
        int projectsScore = auditorData.path("sectionScores").path("projects").asInt();

        // Strict rubric assertions:
        // - Headline under 40 chars, or generic ("Aspiring developer") with no skills: score below 50
        assertThat(headlineScore).isLessThan(50);

        // - About under 100 chars, or containing spelling/grammar errors: below 60
        assertThat(aboutScore).isLessThan(60);

        // - Project with no tech stack, no bullets, or no measurable outcome: below 60
        assertThat(projectsScore).isLessThan(60);

        // - Overall score must be low
        assertThat(overallScore).isLessThan(60);

        // - Sections must not all receive similar scores
        assertThat(headlineScore).isNotEqualTo(aboutScore);
        assertThat(aboutScore).isNotEqualTo(projectsScore);

        // - Auditor must list real weaknesses with exact text and reason
        JsonNode weaknesses = auditorData.path("weaknesses");
        assertThat(weaknesses.isArray()).isTrue();
        assertThat(weaknesses.size()).isGreaterThanOrEqualTo(2);

        boolean foundHeadlineWeakness = false;
        boolean foundAboutWeakness = false;
        boolean foundProjectWeakness = false;
        for (JsonNode w : weaknesses) {
            String sec = w.path("section").asText();
            if ("HEADLINE".equals(sec)) {
                foundHeadlineWeakness = true;
                assertThat(w.path("weakText").asText()).contains("Aspiring developer");
                assertThat(w.path("reason").asText()).isNotEmpty();
            } else if ("ABOUT".equals(sec)) {
                foundAboutWeakness = true;
                assertThat(w.path("weakText").asText()).contains("I love coding");
                assertThat(w.path("reason").asText()).isNotEmpty();
            } else if ("PROJECTS".equals(sec)) {
                foundProjectWeakness = true;
                assertThat(w.path("weakText").asText()).isNotEmpty();
                assertThat(w.path("reason").asText()).isNotEmpty();
            }
        }
        assertThat(foundHeadlineWeakness).isTrue();
        assertThat(foundAboutWeakness).isTrue();
        assertThat(foundProjectWeakness).isTrue();
    }

    @Test
    @DisplayName("Calibration: Cache key changes after editing profile and prompt version changes")
    void testCacheKeyChangesAfterEdit() {
        String profileContent1 = "Headline: Junior Dev\nAbout: Learning Spring Boot\nTarget Role: Backend Engineer";
        String profileContent2 = "Headline: Senior Distributed Systems Architect | Java & Cloud\nAbout: Architected microservices\nTarget Role: Backend Engineer";

        String hash1 = AiExecutionService.computeProfileContentHash(profileContent1);
        String hash2 = AiExecutionService.computeProfileContentHash(profileContent2);

        assertThat(hash1).isNotEqualTo(hash2);

        String cacheKey1 = AiExecutionService.computeAuditCacheKey("v2", hash1);
        String cacheKey2 = AiExecutionService.computeAuditCacheKey("v2", hash2);

        // Cache key changes when profile is edited
        assertThat(cacheKey1).isNotEqualTo(cacheKey2);

        // Prompt version affects cache key
        String cacheKeyV1 = AiExecutionService.computeAuditCacheKey("v1", hash1);
        String cacheKeyV2 = AiExecutionService.computeAuditCacheKey("v2", hash1);
        assertThat(cacheKeyV1).isNotEqualTo(cacheKeyV2);
    }

    @Test
    @DisplayName("Calibration: Unchanged profile detected without duplicate report creation")
    void testUnchangedProfileDetected() throws Exception {
        String email = "unchanged_" + UUID.randomUUID() + "@college.edu";
        String token = registerAndGetToken(email, "Unchanged Candidate");

        ProfileRequest profileReq = ProfileRequest.builder()
                .headline("Full Stack Engineer | React & Spring Boot")
                .about("Passionate engineer building accessible and resilient cloud applications.")
                .targetRole("Full Stack Developer")
                .build();
        mockMvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk());

        ProfileSkillRequest skillReq = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("INTERMEDIATE")
                .build();
        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        Profile profile = profileRepository.findByUserId(user.getId()).orElseThrow();

        // 1. First audit execution
        MvcResult firstResult = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.unchanged").value(false))
                .andReturn();

        Long firstReportId = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .path("reportId").asLong();

        List<AuditReport> reportsAfterFirst = auditReportRepository.findAllByProfileIdOrderByCreatedAtDesc(profile.getId());
        assertThat(reportsAfterFirst).hasSize(1);

        // 2. Second audit execution with UNCHANGED profile
        MvcResult secondResult = mockMvc.perform(post("/api/v1/agents/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(firstReportId))
                .andExpect(jsonPath("$.unchanged").value(true))
                .andReturn();

        // Verify NO duplicate report was created in database
        List<AuditReport> reportsAfterSecond = auditReportRepository.findAllByProfileIdOrderByCreatedAtDesc(profile.getId());
        assertThat(reportsAfterSecond).hasSize(1);
        assertThat(reportsAfterSecond.get(0).getId()).isEqualTo(firstReportId);
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .fullName(fullName)
                .password("Password123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }
}
