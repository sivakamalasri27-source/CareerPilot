package com.careerpilot.agent;

import com.careerpilot.agent.core.AgentContext;
import com.careerpilot.agent.core.AgentResult;
import com.careerpilot.agent.core.AgentStatus;
import com.careerpilot.agent.dto.ParsedResumeResult;
import com.careerpilot.agent.resume.ResumeParserAgent;
import com.careerpilot.ai.client.FakeLlmClient;
import com.careerpilot.entity.Profile;
import com.careerpilot.entity.User;
import com.careerpilot.service.PdfExtractionService;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ResumeParserUnitTest {

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @Autowired
    private ResumeParserAgent resumeParserAgent;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private Cache<String, Object> aiResultCache;

    private User sampleUser;
    private Profile sampleProfile;

    @BeforeEach
    void setUp() {
        fakeLlmClient.reset();
        aiResultCache.invalidateAll();

        sampleUser = User.builder()
                .id(1L)
                .email("resume.tester@college.edu")
                .fullName("Alex Resume")
                .build();

        sampleProfile = Profile.builder()
                .id(20L)
                .user(sampleUser)
                .targetRole("Backend Engineer")
                .build();
    }

    private byte[] createPdfBytes(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null && !text.isBlank()) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    @Test
    @DisplayName("PdfExtractionService: Successfully extracts text from valid PDF")
    void testExtractText_ValidPdf() throws IOException {
        String content = "Senior Software Engineering Student with extensive Java, Spring Boot, and PostgreSQL microservices background.";
        byte[] pdfBytes = createPdfBytes(content);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes
        );

        String extracted = pdfExtractionService.extractText(file);

        assertThat(extracted).isNotNull();
        assertThat(extracted).contains("Senior Software Engineering Student");
        assertThat(extracted.length()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects empty or null file")
    void testExtractText_EmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");

        assertThatThrownBy(() -> pdfExtractionService.extractText(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects non-PDF content type")
    void testExtractText_NonPdfContentType() {
        MockMultipartFile txtFile = new MockMultipartFile(
                "file",
                "resume.pdf",
                "text/plain",
                "Some valid text longer than fifty characters for the file".getBytes()
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(txtFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PDF documents are allowed");
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects non-PDF file extension")
    void testExtractText_NonPdfExtension() {
        MockMultipartFile docFile = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/pdf",
                "%PDF-1.4 header with some fake content longer than fifty characters".getBytes()
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(docFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only .pdf files are accepted");
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects file with bad magic bytes")
    void testExtractText_BadMagicBytes() {
        byte[] fakeBytes = "NOT A VALID PDF HEADER but it is sufficiently long to pass size checks".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                fakeBytes
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(fakeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("magic byte signature mismatch");
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects scanned/empty PDF (< 50 chars)")
    void testExtractText_ScannedOrEmptyPdf() throws IOException {
        byte[] emptyPdfBytes = createPdfBytes(""); // Empty page without text

        MockMultipartFile emptyPdfFile = new MockMultipartFile(
                "file",
                "scanned.pdf",
                "application/pdf",
                emptyPdfBytes
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(emptyPdfFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Scanned image-only PDFs without OCR text layer are not supported");
    }

    @Test
    @DisplayName("PdfExtractionService: Rejects oversized file (> 5 MB)")
    void testExtractText_OversizedFile() {
        byte[] oversizedBytes = new byte[6 * 1024 * 1024]; // 6 MB
        oversizedBytes[0] = 0x25; // %
        oversizedBytes[1] = 0x50; // P
        oversizedBytes[2] = 0x44; // D
        oversizedBytes[3] = 0x46; // F

        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file",
                "large.pdf",
                "application/pdf",
                oversizedBytes
        );

        assertThatThrownBy(() -> pdfExtractionService.extractText(oversizedFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 5 MB limit");
    }

    @Test
    @DisplayName("ResumeParserAgent: Successfully parses resume text into structured DTO")
    void testResumeParserAgent_ValidExecution() {
        String resumeText = """
                Alex Chen - Aspiring Software Engineer
                alex.chen@university.edu | github.com/alexchen
                Skills: Java, Spring Boot, Docker, PostgreSQL
                Projects: Cloud Microservices Platform - Engineered RESTful APIs with Docker deployment
                Education: State University, Bachelor of Science in Computer Science, 2021-2025
                """;

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input(resumeText)
                .targetRole("Backend Engineer")
                .build();

        AgentResult<ParsedResumeResult> result = resumeParserAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.SUCCESS);
        assertThat(result.getData()).isNotNull();

        ParsedResumeResult parsed = result.getData();
        assertThat(parsed.getHeadline()).contains("Junior Software Engineer");
        assertThat(parsed.getSkills()).isNotEmpty();
        assertThat(parsed.getProjects()).isNotEmpty();
        assertThat(parsed.getEducation()).isNotEmpty();
    }

    @Test
    @DisplayName("ResumeParserAgent: Returns failure when given empty text")
    void testResumeParserAgent_EmptyText() {
        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input("   ")
                .targetRole("Backend Engineer")
                .build();

        AgentResult<ParsedResumeResult> result = resumeParserAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Cannot parse empty resume text");
    }

    @Test
    @DisplayName("ResumeParserAgent: Falls back gracefully when LLM returns malformed JSON")
    void testResumeParserAgent_FallbackOnMalformedJson() {
        fakeLlmClient.setDefaultResponse("{ invalid json string without closing brackets");

        AgentContext<String> ctx = AgentContext.<String>builder()
                .user(sampleUser)
                .profile(sampleProfile)
                .input("Valid resume text exceeding the required minimum characters to test fallback parsing.")
                .targetRole("Backend Engineer")
                .build();

        AgentResult<ParsedResumeResult> result = resumeParserAgent.execute(ctx);

        assertThat(result.getStatus()).isEqualTo(AgentStatus.FALLBACK);
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getSkills()).isEmpty();
    }
}
