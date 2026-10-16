package com.careerpilot.agent;

import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.dto.auth.AuthResponse;
import com.careerpilot.dto.auth.RegisterRequest;
import com.careerpilot.dto.profile.ProfileSkillRequest;
import com.careerpilot.dto.profile.ProjectRequest;
import com.careerpilot.dto.resume.ResumeUploadResponse;
import com.careerpilot.entity.AgentSuggestion;
import com.careerpilot.entity.ResumeUpload;
import com.careerpilot.entity.SuggestionStatus;
import com.careerpilot.entity.UploadStatus;
import com.careerpilot.repository.AgentSuggestionRepository;
import com.careerpilot.repository.ResumeUploadRepository;
import com.careerpilot.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private ResumeUploadRepository resumeUploadRepository;

    @Autowired
    private AgentSuggestionRepository agentSuggestionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Cache<String, Object> aiResultCache;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();
    }

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

    private String registerAndGetToken(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@college.edu";
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password("Password123!")
                .fullName("Resume Candidate")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getAccessToken();
    }

    @Test
    @DisplayName("Should return 401 when uploading resume without token")
    void testUploadResume_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createPdfBytes("Valid candidate resume content text over fifty characters long.")
        );

        mockMvc.perform(multipart("/api/v1/resume/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 400 when uploading non-PDF file")
    void testUploadResume_NonPdf() throws Exception {
        String token = registerAndGetToken("nonpdf");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "Some plain text resume content that is longer than fifty characters.".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Only PDF documents are allowed")));
    }

    @Test
    @DisplayName("Should return 400 when uploading PDF with bad magic bytes")
    void testUploadResume_BadMagicBytes() throws Exception {
        String token = registerAndGetToken("badmagic");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrupted.pdf",
                "application/pdf",
                "NOT REAL PDF BYTES but plenty long to pass any simple byte count checks".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("magic byte signature mismatch")));
    }

    @Test
    @DisplayName("Should return 400 when uploading scanned or empty PDF (< 50 chars)")
    void testUploadResume_EmptyOrScannedPdf() throws Exception {
        String token = registerAndGetToken("scanned");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "scanned.pdf",
                "application/pdf",
                createPdfBytes("") // Empty PDF page without text
        );

        mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Scanned image-only PDFs without OCR text layer are not supported")));
    }

    @Test
    @DisplayName("Should upload valid PDF, extract text in-memory, and create PENDING suggestions")
    void testUploadResume_Success() throws Exception {
        String token = registerAndGetToken("resumesuccess");

        String resumeContent = "Alex Chen - Backend Engineer. Hands-on experience with Java, Docker, and Spring Boot microservices. Graduated from State University with Bachelor of Science.";
        byte[] pdfBytes = createPdfBytes(resumeContent);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alex_chen_resume.pdf",
                "application/pdf",
                pdfBytes
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fileName").value("alex_chen_resume.pdf"))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.createdSuggestionsCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.suggestions").isArray())
                .andReturn();

        ResumeUploadResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ResumeUploadResponse.class);

        // Verify database persistence of upload record
        ResumeUpload upload = resumeUploadRepository.findById(response.getId()).orElse(null);
        assertThat(upload).isNotNull();
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.PROCESSED);
        assertThat(upload.getParsedText()).contains("Alex Chen");

        // Verify that suggestions were saved as PENDING
        List<AgentSuggestion> suggestions = agentSuggestionRepository.findAllByProfileIdAndStatus(upload.getUser().getId(), SuggestionStatus.PENDING);
        // Note: query by profile or user
        assertThat(response.getSuggestions()).allMatch(s -> s.getStatus() == SuggestionStatus.PENDING);
    }

    @Test
    @DisplayName("Should skip duplicates when imported skills or projects already exist on profile")
    void testUploadResume_SkipDuplicates() throws Exception {
        String token = registerAndGetToken("duplicateskip");

        // 1. Pre-add skill "Java" to profile
        ProfileSkillRequest skillRequest = ProfileSkillRequest.builder()
                .name("Java")
                .claimedLevel("ADVANCED")
                .build();

        mockMvc.perform(post("/api/v1/profile/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(skillRequest)))
                .andExpect(status().isCreated());

        // 2. Pre-add project "Cloud Microservices Platform" to profile
        ProjectRequest projectRequest = ProjectRequest.builder()
                .title("Cloud Microservices Platform")
                .description("Existing distributed platform")
                .technologies("Java, Docker")
                .bullets(List.of("Existing bullet"))
                .build();

        mockMvc.perform(post("/api/v1/profile/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectRequest)))
                .andExpect(status().isCreated());

        // 3. Upload resume (canned LLM response contains Java and Docker skills, and Cloud Microservices Platform project)
        String resumeContent = "Alex Chen - Backend Engineer. Built Cloud Microservices Platform in Java and Docker. State University graduate.";
        byte[] pdfBytes = createPdfBytes(resumeContent);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume_with_duplicates.pdf",
                "application/pdf",
                pdfBytes
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        ResumeUploadResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ResumeUploadResponse.class);

        // Verify: Skill "Java" was skipped, Docker was imported. Project "Cloud Microservices Platform" was skipped.
        boolean containsJavaSuggestion = response.getSuggestions().stream()
                .anyMatch(s -> "SKILL".equals(s.getTargetSection()) && s.getSuggestedText().toLowerCase().contains("java"));
        assertThat(containsJavaSuggestion).isFalse();

        boolean containsDockerSuggestion = response.getSuggestions().stream()
                .anyMatch(s -> "SKILL".equals(s.getTargetSection()) && s.getSuggestedText().toLowerCase().contains("docker"));
        assertThat(containsDockerSuggestion).isTrue();

        boolean containsProjectSuggestion = response.getSuggestions().stream()
                .anyMatch(s -> "PROJECT".equals(s.getTargetSection()) && s.getSuggestedText().toLowerCase().contains("cloud microservices platform"));
        assertThat(containsProjectSuggestion).isFalse();
    }

    @Test
    @DisplayName("Should retrieve resume upload history only for authenticated user")
    void testResumeHistory_OwnershipIsolation() throws Exception {
        String tokenUserA = registerAndGetToken("user_a");
        String tokenUserB = registerAndGetToken("user_b");

        byte[] pdfBytes = createPdfBytes("Resume content for user A with sufficient length to pass the minimum characters check.");
        MockMultipartFile file = new MockMultipartFile("file", "user_a_resume.pdf", "application/pdf", pdfBytes);

        mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isCreated());

        // User A should see 1 upload
        mockMvc.perform(get("/api/v1/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName").value("user_a_resume.pdf"));

        // User B should see 0 uploads
        mockMvc.perform(get("/api/v1/resume")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Should delete resume upload when requested by owner; return 404 for another user")
    void testDeleteResume_OwnerAndOwnershipEnforcement() throws Exception {
        String tokenOwner = registerAndGetToken("owner_del");
        String tokenAttacker = registerAndGetToken("attacker_del");

        byte[] pdfBytes = createPdfBytes("Resume content for deletion tests with sufficient length to pass validation.");
        MockMultipartFile file = new MockMultipartFile("file", "delete_test.pdf", "application/pdf", pdfBytes);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/resume/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isCreated())
                .andReturn();

        ResumeUploadResponse response = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), ResumeUploadResponse.class);
        Long uploadId = response.getId();

        // User B tries to delete User A's upload -> 404
        mockMvc.perform(delete("/api/v1/resume/" + uploadId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAttacker))
                .andExpect(status().isNotFound());

        // Owner deletes upload -> 204
        mockMvc.perform(delete("/api/v1/resume/" + uploadId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNoContent());

        // Confirm deletion from DB
        assertThat(resumeUploadRepository.findById(uploadId)).isEmpty();

        // Deleting already deleted upload -> 404
        mockMvc.perform(delete("/api/v1/resume/" + uploadId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOwner))
                .andExpect(status().isNotFound());
    }
}
