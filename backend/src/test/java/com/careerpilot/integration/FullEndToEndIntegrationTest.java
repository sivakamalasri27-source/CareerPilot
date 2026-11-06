package com.careerpilot.integration;

import com.careerpilot.dto.agent.AuditRequest;
import com.careerpilot.dto.agent.RecruiterSimulationRequest;
import com.careerpilot.dto.agent.TranslateExperienceRequest;
import com.careerpilot.dto.auth.LoginRequest;
import com.careerpilot.dto.auth.RefreshTokenRequest;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.*;
import com.careerpilot.dto.skillgap.SkillGapRequest;
import com.careerpilot.dto.suggestion.ApproveSuggestionRequest;
import com.careerpilot.entity.SuggestionStatus;
import com.careerpilot.entity.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String accessToken;
    private static String refreshToken;
    private static String testEmail = "e2e.candidate@careerpilot.dev";
    private static Long projectId;
    private static Long suggestionId;
    private static Long weeklyTaskId;

    private byte[] createPdfBytes(String content) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (content != null && !content.isBlank()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    String singleLine = content.replace('\n', ' ').replace('\r', ' ');
                    cs.showText(singleLine);
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Flow 1: User Registration, Login, Token Refresh, and Profile Inspection")
    void testAuthFlow() throws Exception {
        // 1. Register
        RegisterRequest registerRequest = RegisterRequest.builder()
                .fullName("E2E Test Candidate")
                .email(testEmail)
                .password("Password123!")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode registerNode = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        refreshToken = registerNode.get("refreshToken").asText();

        // 2. Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email(testEmail)
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        accessToken = loginNode.get("accessToken").asText();

        // 3. Token Refresh with Rotation
        RefreshTokenRequest refreshReq = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode refreshNode = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        accessToken = refreshNode.get("accessToken").asText();
        refreshToken = refreshNode.get("refreshToken").asText();

        // 4. Access Authenticated Endpoint
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test
    @Order(2)
    @DisplayName("Flow 2: Candidate Profile Builder CRUD (Bio, Skills, Projects, Education)")
    void testProfileCrudFlow() throws Exception {
        // 1. Update Profile Basics
        ProfileRequest profileReq = ProfileRequest.builder()
                .headline("Aspiring Full Stack Java Engineer | Spring Boot, React, Docker")
                .about("Passionate about microservices architecture and clean code principles.")
                .githubUsername("e2e-octocat")
                .targetRole("Java Backend Developer")
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value(profileReq.getHeadline()))
                .andExpect(jsonPath("$.githubUsername").value("e2e-octocat"));

        // 2. Add and Update Skills
        ProfileSkillRequest skillReq = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("ADVANCED")
                .build();

        MvcResult skillResult = mockMvc.perform(post("/api/v1/profile/skills")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Java"))
                .andReturn();

        // 3. Add Project with Bullets
        ProjectRequest projectReq = ProjectRequest.builder()
                .title("Distributed Event Processing Engine")
                .role("Lead Backend Engineer")
                .technologies("Java, Spring Boot, Kafka, Redis")
                .bullets(List.of("Engineered pub-sub pipeline processing 5,000 events/sec with sub-50ms latency."))
                .build();

        MvcResult projectResult = mockMvc.perform(post("/api/v1/profile/projects")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Distributed Event Processing Engine"))
                .andReturn();

        JsonNode projNode = objectMapper.readTree(projectResult.getResponse().getContentAsString());
        projectId = projNode.get("id").asLong();

        // 4. Add Education Record
        EducationRequest eduReq = EducationRequest.builder()
                .degree("Bachelor of Technology in Computer Science")
                .institution("National Institute of Technology")
                .fieldOfStudy("Computer Science")
                .gradeOrGpa("3.9 / 4.0")
                .build();

        mockMvc.perform(post("/api/v1/profile/education")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eduReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.degree").value(eduReq.getDegree()));
    }

    @Test
    @Order(3)
    @DisplayName("Flow 3: Full Multi-Agent Audit and Report Persistence")
    void testAgentAuditFlow() throws Exception {
        AuditRequest auditReq = AuditRequest.builder()
                .targetRole("Java Backend Developer")
                .build();

        MvcResult auditResult = mockMvc.perform(post("/api/v1/agents/audit")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").isNotEmpty())
                .andExpect(jsonPath("$.auditorResult.data.overallScore").isNumber())
                .andExpect(jsonPath("$.auditorResult.data.sectionScores").isMap())
                .andReturn();

        JsonNode auditNode = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        long reportId = auditNode.get("reportId").asLong();

        // Verify retrieval from /api/v1/reports
        mockMvc.perform(get("/api/v1/reports/" + reportId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.overallScore").isNumber());
    }

    @Test
    @Order(4)
    @DisplayName("Flow 4: Recruiter Simulation Panel (MNC HR, Founder, Tech Lead)")
    void testRecruiterSimulationFlow() throws Exception {
        RecruiterSimulationRequest req = RecruiterSimulationRequest.builder()
                .targetRole("Java Backend Developer")
                .build();

        mockMvc.perform(post("/api/v1/agents/recruiter-simulation")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiterResult.data.personas").isArray())
                .andExpect(jsonPath("$.recruiterResult.data.personas", hasSize(3)));
    }

    @Test
    @Order(5)
    @DisplayName("Flow 5: Experience Translation, Suggestion Creation, and Approval Workflow")
    void testExperienceTranslationAndSuggestionFlow() throws Exception {
        TranslateExperienceRequest transReq = TranslateExperienceRequest.builder()
                .projectId(projectId)
                .build();

        MvcResult transResult = mockMvc.perform(post("/api/v1/agents/translate-experience")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestion.id").isNotEmpty())
                .andExpect(jsonPath("$.agentResult.data.bullets").isArray())
                .andReturn();

        JsonNode transNode = objectMapper.readTree(transResult.getResponse().getContentAsString());
        suggestionId = transNode.get("suggestion").get("id").asLong();

        // 1. Inspect suggestion
        mockMvc.perform(get("/api/v1/suggestions/" + suggestionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SuggestionStatus.PENDING.name()));

        // 2. Approve suggestion
        ApproveSuggestionRequest approveReq = ApproveSuggestionRequest.builder()
                .editedText("Architected robust event-driven pipeline handling 5,000 TPS.")
                .build();

        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SuggestionStatus.APPROVED.name()));

        // 3. Second decision should return 409 Conflict
        mockMvc.perform(patch("/api/v1/suggestions/" + suggestionId + "/approve")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(6)
    @DisplayName("Flow 6: In-Memory Resume PDF Upload and Ingestion")
    void testResumeUploadFlow() throws Exception {
        String resumeContent = "Alex Chen - Backend Engineer. Hands-on experience with Java, Docker, and Spring Boot microservices. Graduated from State University with Bachelor of Science.";
        byte[] pdfBytes = createPdfBytes(resumeContent);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "candidate_resume.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                pdfBytes
        );

        mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("candidate_resume.pdf"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));

        // Verify history
        mockMvc.perform(get("/api/v1/resume")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(7)
    @DisplayName("Flow 7: Curated Roles Catalog & Deterministic Skill Gap Analysis")
    void testSkillGapFlow() throws Exception {
        // 1. List curated roles
        MvcResult rolesResult = mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andReturn();

        JsonNode rolesNode = objectMapper.readTree(rolesResult.getResponse().getContentAsString());
        long roleId = rolesNode.get(0).get("id").asLong();

        // 2. Run Skill Gap Analysis
        SkillGapRequest gapReq = SkillGapRequest.builder()
                .roleId(roleId)
                .build();

        mockMvc.perform(post("/api/v1/agents/skill-gap")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gapReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andExpect(jsonPath("$.matchPercentage").isNumber())
                .andExpect(jsonPath("$.learningRoadmap").isArray());
    }

    @Test
    @Order(8)
    @DisplayName("Flow 8: Weekly Coach Plan Generation, Idempotency, and Consistency Streak")
    void testWeeklyCoachFlow() throws Exception {
        // 1. Generate 3 tasks
        MvcResult planResult = mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.newPlan").value(true))
                .andReturn();

        JsonNode planNode = objectMapper.readTree(planResult.getResponse().getContentAsString());
        weeklyTaskId = planNode.get("tasks").get(0).get("id").asLong();

        // 2. Calling again within same ISO week returns same tasks (newPlan = false)
        mockMvc.perform(post("/api/v1/coach/weekly-tasks/generate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newPlan").value(false));

        // 3. Mark a task complete
        mockMvc.perform(patch("/api/v1/coach/weekly-tasks/" + weeklyTaskId + "/complete")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TaskStatus.DONE.name()));

        // 4. Calculate streak
        mockMvc.perform(get("/api/v1/coach/streak")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeekCompletedTasks").value(1));
    }
}
