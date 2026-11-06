package com.careerpilot.ai.client;

import com.careerpilot.ai.dto.LlmRequest;
import com.careerpilot.ai.dto.LlmResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@Slf4j
public class FakeLlmClient implements LlmClient {

    private static final String DEFAULT_CANNED_RESPONSE = """
            {
              "score": 85,
              "summary": "Solid foundation in Java and Spring Boot with verifiable project evidence.",
              "recommendations": [
                "Include unit test coverage metrics in project bullets",
                "Highlight deployment links in resume"
              ],
              "missingFields": [
                "Certifications"
              ]
            }
            """;

    public static final String CANNED_AUDITOR_RESPONSE = """
            {
              "overallScore": 85,
              "sectionScores": {
                "headline": 80,
                "about": 85,
                "skills": 90,
                "projects": 85,
                "education": 80
              },
              "weaknesses": [
                {
                  "section": "PROJECTS",
                  "weakText": "Built REST API with Spring Boot",
                  "reason": "Lacks deployment link and quantifiable metrics",
                  "improvementSuggestion": "Add Docker deployment link and unit test coverage percentage"
                }
              ],
              "strengths": [
                "Strong foundation in Spring Boot and relational databases"
              ],
              "keyRecommendations": [
                "Include unit test coverage metrics in project bullets"
              ]
            }
            """;

    public static final String CANNED_RECRUITER_RESPONSE = """
            {
              "personas": [
                {
                  "persona": "MNC_HR",
                  "personaTitle": "MNC HR Recruiter",
                  "firstImpression10Sec": "Clean formatting and solid academic qualifications.",
                  "strengths": ["Clear educational pedigree and foundational skills"],
                  "redFlags": ["No commercial enterprise experience"],
                  "decision": "YES",
                  "reasoning": "Fits standard entry-level hiring criteria."
                },
                {
                  "persona": "STARTUP_FOUNDER",
                  "personaTitle": "Startup Founder",
                  "firstImpression10Sec": "Demonstrates self-starter initiative with practical projects.",
                  "strengths": ["Built end-to-end fullstack features"],
                  "redFlags": ["Could demonstrate higher speed with live links"],
                  "decision": "YES",
                  "reasoning": "Displays ownership and genuine interest in building products."
                },
                {
                  "persona": "TECH_LEAD",
                  "personaTitle": "Technical Lead",
                  "firstImpression10Sec": "Properly structured backend architecture.",
                  "strengths": ["Understanding of Spring Data JPA and JWT auth"],
                  "redFlags": ["Need to verify depth of SQL optimization"],
                  "decision": "YES",
                  "reasoning": "Demonstrates solid junior software engineering abilities."
                }
              ],
              "consensusDecision": "YES",
              "summary": "Positive hiring consensus for an entry-level software engineering role."
            }
            """;

    public static final String CANNED_TRANSLATOR_RESPONSE = """
            {
              "bullets": [
                "Architected distributed microservices backend with Spring Boot and PostgreSQL, achieving sub-50ms API response latency",
                "Engineered robust JWT authentication filter with SHA-256 refresh token rotation and real-time reuse detection",
                "Implemented automated CI/CD pipeline using Docker and GitHub Actions, automating regression testing"
              ],
              "clarifyingQuestions": [
                "What was the estimated user traffic or throughput handled by the service?",
                "Did you measure automated unit and integration test coverage percentage?"
              ],
              "rationale": "Transformed basic task descriptions into STAR achievement bullets with quantifiable outcomes and precise technical architecture."
            }
            """;

    public static final String CANNED_PROOF_RESPONSE = """
            {
              "overallVerifiedPercentage": 100.0,
              "skills": [
                {
                  "skillName": "java",
                  "claimedLevel": "INTERMEDIATE",
                  "status": "VERIFIED",
                  "evidence": [
                    {
                      "repoName": "ecommerce-api",
                      "repoUrl": "https://github.com/testuser/ecommerce-api",
                      "reason": "Primary language: Java"
                    }
                  ],
                  "explanation": "Verified through primary language Java implementation in repository ecommerce-api."
                }
              ],
              "summary": "Verified 1 of 1 claimed skills against public GitHub activity."
            }
            """;

    public static final String CANNED_RESUME_PARSER_RESPONSE = """
            {
              "headline": "Junior Software Engineer | Java & Spring Boot",
              "about": "Aspiring software engineer with hands-on experience building distributed systems.",
              "skills": [
                {
                  "name": "Java",
                  "proficiency": "INTERMEDIATE"
                },
                {
                  "name": "Docker",
                  "proficiency": "BEGINNER"
                }
              ],
              "projects": [
                {
                  "title": "Cloud Microservices Platform",
                  "description": "Backend services built with Spring Boot and PostgreSQL",
                  "technologies": "Java, Spring Boot, Docker, PostgreSQL",
                  "role": "Backend Developer",
                  "bullets": [
                    "Engineered RESTful endpoints handling high concurrency",
                    "Configured Docker container deployment"
                  ]
                }
              ],
              "education": [
                {
                  "institution": "State University",
                  "degree": "Bachelor of Science",
                  "fieldOfStudy": "Computer Science",
                  "gradeOrGpa": "3.8/4.0",
                  "startDate": "2021-09-01",
                  "endDate": "2025-05-31"
                }
              ]
            }
            """;

    public static final String CANNED_SKILL_GAP_RESPONSE = """
            {
              "roleId": 1,
              "roleName": "Java Backend Developer",
              "matchPercentage": 75.0,
              "matchedSkills": [
                {
                  "skillName": "java",
                  "claimedLevel": "INTERMEDIATE",
                  "verificationStatus": "VERIFIED",
                  "weight": 3
                },
                {
                  "skillName": "spring boot",
                  "claimedLevel": "INTERMEDIATE",
                  "verificationStatus": "VERIFIED",
                  "weight": 3
                }
              ],
              "missingSkills": [
                {
                  "skillName": "docker",
                  "category": "DevOps",
                  "priority": "IMPORTANT",
                  "weight": 2
                }
              ],
              "weakSkills": [],
              "learningRoadmap": [
                {
                  "skillName": "docker",
                  "priority": "IMPORTANT",
                  "whyItMatters": "Containerization is required for scalable microservice deployments in cloud platforms.",
                  "whatToLearn": "Dockerfile syntax, multi-stage builds, and docker-compose orchestration.",
                  "smallProjectIdea": "Containerize a Spring Boot service with PostgreSQL and run via docker-compose up."
                }
              ],
              "summary": "Solid 75% alignment with Java Backend Developer. Master Docker containerization to complete your foundational backend stack."
            }
            """;

    public static final String CANNED_WEEKLY_COACH_RESPONSE = """
            {
              "tasks": [
                {
                  "title": "Containerize Spring Boot Service with Dockerfile",
                  "description": "Write a multi-stage Dockerfile packaging your Spring Boot application into a clean container image.",
                  "category": "PROJECT_IMPROVEMENT",
                  "estimatedTime": "~1-2 hours",
                  "whyThisMatters": "Containerization proves you can package applications for real-world deployment.",
                  "verificationType": "GITHUB_FILE_EXISTS",
                  "verificationHint": "Dockerfile"
                },
                {
                  "title": "Add README with Architecture Diagram and Run Steps",
                  "description": "Create a clear README.md with setup prerequisites, environment variables, and Docker Compose instructions.",
                  "category": "PROFILE_OPTIMIZATION",
                  "estimatedTime": "~1 hour",
                  "whyThisMatters": "A polished README makes your repository recruiter-friendly within 30 seconds.",
                  "verificationType": "GITHUB_FILE_EXISTS",
                  "verificationHint": "README.md"
                },
                {
                  "title": "Write Unit Tests for One Service Class",
                  "description": "Implement JUnit 5 tests covering one service class with happy path and edge cases using Mockito.",
                  "category": "SKILL_BUILDING",
                  "estimatedTime": "~1-2 hours",
                  "whyThisMatters": "Demonstrates test discipline and safeguards domain logic against regressions.",
                  "verificationType": "GITHUB_RECENT_COMMIT",
                  "verificationHint": null
                }
              ],
              "focusArea": "Containerization & Verification",
              "rationale": "Targeting the top skill gap (Docker) and addressing audit weakness regarding test coverage."
            }
            """;

    public static final String CANNED_JOB_MATCH_RESPONSE = """
            {
              "fitSummary": "Strong alignment with core backend requirements including Java and relational databases. Bridging missing containerization and microservices experience will make this candidate highly competitive.",
              "profileTweaks": [
                "Feature your Spring Boot REST API project at the top of your profile to align directly with the primary backend stack mentioned in the job description.",
                "Detail your database query tuning and PostgreSQL indexing experience in your project bullets to match the data persistence requirements.",
                "Clarify your hands-on experience with Git version control and team collaboration workflows in your about summary."
              ],
              "interviewTopics": [
                "Spring Boot dependency injection and bean lifecycle",
                "RESTful API design, idempotency, and error handling",
                "PostgreSQL indexing, transaction isolation levels, and query optimization",
                "Unit and integration testing best practices using JUnit and Mockito"
              ]
            }
            """;

    @Getter
    private final List<LlmRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());
    private final Queue<String> responseQueue = new LinkedList<>();
    private final Map<String, Queue<String>> agentQueues = new java.util.concurrent.ConcurrentHashMap<>();

    @Setter
    private String defaultResponse = DEFAULT_CANNED_RESPONSE;

    @Setter
    private boolean simulateTimeout = false;

    @Setter
    private boolean simulateServerError = false;

    @Setter
    private boolean simulateClientError = false;

    @Setter
    private Integer simulateStatusCode = null;

    @Setter
    private String simulateStatusMessage = null;

    @Override
    public String getProviderName() {
        return "fake";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        recordedRequests.add(request);

        if (simulateStatusCode != null) {
            int code = simulateStatusCode;
            String msg = simulateStatusMessage != null ? simulateStatusMessage : "Simulated HTTP " + code;
            if (code == 401 || code == 403) {
                log.error("Gemini rejected the API key");
                throw new GeminiApiException("Gemini rejected the API key", code);
            } else if (code == 400) {
                String body = "{\"error\":{\"code\":400,\"message\":\"Model not supported for this API version\",\"status\":\"INVALID_ARGUMENT\"}}";
                log.error("Gemini returned 400 Bad Request for model 'gemini-test'. Response body: {}", body);
                throw new GeminiApiException(
                        "Gemini returned HTTP 400 for model 'gemini-test'. Response: " + body
                        + ". Valid models: [models/gemini-2.5-flash]",
                        400, body);
            } else if (code == 404) {
                log.error("Model 'gemini-test' not found");
                throw new GeminiApiException("Model 'gemini-test' not found. Available models: [models/gemini-2.5-flash]", code);
            } else if (code == 429) {
                log.error("Gemini API quota exceeded (HTTP 429)");
                throw new GeminiApiException("Gemini API quota exceeded (HTTP 429)", code);
            } else if (code >= 500) {
                throw HttpServerErrorException.create(
                        HttpStatusCode.valueOf(code), msg, HttpHeaders.EMPTY, new byte[0], null);
            } else {
                throw HttpClientErrorException.create(
                        HttpStatusCode.valueOf(code), msg, HttpHeaders.EMPTY, new byte[0], null);
            }
        }

        if (simulateTimeout) {
            throw new ResourceAccessException("Simulated read timeout connecting to LLM service");
        }

        if (simulateServerError) {
            throw HttpServerErrorException.create(
                    HttpStatusCode.valueOf(503), "Simulated 503 Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);
        }

        if (simulateClientError) {
            throw HttpClientErrorException.create(
                    HttpStatusCode.valueOf(400), "Simulated 400 Bad Request", HttpHeaders.EMPTY, new byte[0], null);
        }

        String content = null;
        String prompt = request.getPrompt();

        // Check if targeted queue exists for Auditor, Recruiter, Translator, ProofOfSkill, ResumeParser, SkillGap, or WeeklyCoach
        if (prompt != null) {
            if (prompt.contains("ProfileAuditorAgent") || prompt.contains("overallScore")) {
                Queue<String> q = agentQueues.get("ProfileAuditorAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("RecruiterSimulatorAgent") || prompt.contains("MNC_HR") || prompt.contains("hiring committee")) {
                Queue<String> q = agentQueues.get("RecruiterSimulatorAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("ExperienceTranslatorAgent") || prompt.contains("STAR / XYZ BULLET STRUCTURE") || prompt.contains("clarifyingQuestions")) {
                Queue<String> q = agentQueues.get("ExperienceTranslatorAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("ProofOfSkillAgent") || prompt.contains("overallVerifiedPercentage") || prompt.contains("Skill evidence data computed")) {
                Queue<String> q = agentQueues.get("ProofOfSkillAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("ResumeParserAgent") || prompt.contains("Resume text data:") || prompt.contains("SKILLS EXTRACTION")) {
                Queue<String> q = agentQueues.get("ResumeParserAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("SkillGapAgent") || prompt.contains("learningRoadmap") || prompt.contains("Skill gap computation data:")) {
                Queue<String> q = agentQueues.get("SkillGapAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            } else if (prompt.contains("WeeklyCoachAgent") || prompt.contains("EXACTLY THREE CONCRETE TASKS") || prompt.contains("WeeklyCoachAgent for CareerPilot")) {
                Queue<String> q = agentQueues.get("WeeklyCoachAgent");
                if (q != null && !q.isEmpty()) {
                    content = q.poll();
                }
            }
        }

        if (content == null) {
            synchronized (responseQueue) {
                if (!responseQueue.isEmpty()) {
                    content = responseQueue.poll();
                } else if (!DEFAULT_CANNED_RESPONSE.equals(defaultResponse)) {
                    content = defaultResponse;
                } else {
                    if (prompt != null && (prompt.contains("ProfileAuditorAgent") || prompt.contains("overallScore") || prompt.contains("auditor.v2"))) {
                        if (isWeakProfile(prompt)) {
                            content = generateCalibratedAuditorResponse(prompt);
                        } else {
                            content = CANNED_AUDITOR_RESPONSE;
                        }
                    } else if (prompt != null && (prompt.contains("RecruiterSimulatorAgent") || prompt.contains("MNC_HR") || prompt.contains("hiring committee"))) {
                        content = CANNED_RECRUITER_RESPONSE;
                    } else if (prompt != null && (prompt.contains("ExperienceTranslatorAgent") || prompt.contains("STAR / XYZ BULLET STRUCTURE") || prompt.contains("clarifyingQuestions"))) {
                        content = CANNED_TRANSLATOR_RESPONSE;
                    } else if (prompt != null && (prompt.contains("ProofOfSkillAgent") || prompt.contains("overallVerifiedPercentage") || prompt.contains("Skill evidence data computed"))) {
                        content = CANNED_PROOF_RESPONSE;
                    } else if (prompt != null && (prompt.contains("ResumeParserAgent") || prompt.contains("Resume text data:") || prompt.contains("SKILLS EXTRACTION"))) {
                        content = CANNED_RESUME_PARSER_RESPONSE;
                    } else if (prompt != null && (prompt.contains("SkillGapAgent") || prompt.contains("learningRoadmap") || prompt.contains("Skill gap computation data:"))) {
                        content = CANNED_SKILL_GAP_RESPONSE;
                    } else if (prompt != null && (prompt.contains("WeeklyCoachAgent") || prompt.contains("EXACTLY THREE CONCRETE TASKS") || prompt.contains("WeeklyCoachAgent for CareerPilot"))) {
                        content = CANNED_WEEKLY_COACH_RESPONSE;
                    } else if (prompt != null && (prompt.contains("JobMatchAgent") || prompt.contains("job_match") || prompt.contains("JOB_DESCRIPTION") || prompt.contains("profileTweaks"))) {
                        content = CANNED_JOB_MATCH_RESPONSE;
                    } else {
                        content = defaultResponse;
                    }
                }
            }
        }

        int promptTokens = request.getPrompt() != null ? request.getPrompt().length() / 4 : 50;
        int completionTokens = content != null ? content.length() / 4 : 20;

        return LlmResponse.builder()
                .content(content)
                .modelName(request.getModel() != null ? request.getModel() : "fake-model")
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .latencyMs(15L)
                .build();
    }

    public void queueResponse(String response) {
        synchronized (responseQueue) {
            responseQueue.add(response);
        }
    }

    public void queueResponses(List<String> responses) {
        synchronized (responseQueue) {
            responseQueue.addAll(responses);
        }
    }

    public void queueResponseForAgent(String agentName, String response) {
        agentQueues.computeIfAbsent(agentName, k -> new LinkedList<>()).add(response);
    }

    public void reset() {
        recordedRequests.clear();
        synchronized (responseQueue) {
            responseQueue.clear();
        }
        agentQueues.clear();
        this.defaultResponse = DEFAULT_CANNED_RESPONSE;
        this.simulateTimeout = false;
        this.simulateServerError = false;
        this.simulateClientError = false;
        this.simulateStatusCode = null;
        this.simulateStatusMessage = null;
    }

    public static boolean isWeakProfile(String prompt) {
        if (prompt == null) return false;
        String headline = extractField(prompt, "Headline:");
        String about = extractField(prompt, "About/Summary:");
        boolean weakHeadline = headline.length() < 40 ||
                (headline.toLowerCase().contains("aspiring developer") &&
                 !headline.toLowerCase().matches(".*(java|spring|react|python|backend|frontend|fullstack|cloud|engineer|developer\\s*\\|).*"));
        boolean weakAbout = about.length() < 100 && (about.contains("I love coding") || about.length() < 40);
        boolean weakProject = prompt.contains("--- PROJECTS ---") &&
                (prompt.contains("Simple App") || (!prompt.contains("Technologies:") && !prompt.contains("Bullet Points:")));

        return (weakHeadline && (weakAbout || weakProject)) || prompt.contains("Weak Candidate") || prompt.contains("Simple App");
    }

    public static String generateCalibratedAuditorResponse(String prompt) {
        if (prompt == null || !prompt.contains("Headline:")) {
            return CANNED_AUDITOR_RESPONSE;
        }

        String headline = extractField(prompt, "Headline:");
        String about = extractField(prompt, "About/Summary:");

        // Rubric 1: Headline under 40 chars, or generic ("Aspiring developer") with no skills or keywords: score below 50.
        boolean headlineWeak = false;
        int headlineScore = 88;
        String weakHeadlineReason = null;
        if (headline.length() < 40) {
            headlineWeak = true;
            weakHeadlineReason = "Headline is under 40 characters (" + headline.length() + " chars) and lacks professional depth.";
        } else {
            String lowerH = headline.toLowerCase();
            if (lowerH.contains("aspiring developer") || lowerH.contains("student") || lowerH.contains("seeking")) {
                boolean hasKeywords = lowerH.contains("java") || lowerH.contains("spring") || lowerH.contains("backend") || lowerH.contains("cloud") || lowerH.contains("fullstack");
                if (!hasKeywords) {
                    headlineWeak = true;
                    weakHeadlineReason = "Headline uses generic phrasing ('" + headline + "') with no technical skills or specialization keywords.";
                }
            }
        }
        if (headlineWeak) {
            headlineScore = Math.min(48, Math.max(30, 25 + headline.length() / 2));
        }

        // Rubric 2: About under 100 chars, or containing spelling/grammar errors: below 60.
        boolean aboutWeak = false;
        int aboutScore = 86;
        String weakAboutReason = null;
        if (about.length() < 100) {
            aboutWeak = true;
            weakAboutReason = "About section is under 100 characters (" + about.length() + " chars) and fails to convey technical background.";
        } else {
            String lowerA = about.toLowerCase();
            if (lowerA.contains("develepor") || lowerA.contains("programing") || lowerA.contains("experiance") || lowerA.contains("teh") || lowerA.contains("enginer")) {
                aboutWeak = true;
                weakAboutReason = "About section contains spelling and grammatical errors.";
            }
        }
        if (aboutWeak) {
            aboutScore = Math.min(58, Math.max(35, 30 + about.length() / 4));
        }

        // Rubric 3: Project with no tech stack, no bullets, or no measurable outcome: below 60.
        boolean projectWeak = false;
        int projectScore = 90;
        String weakProjectReason = null;
        String weakProjectText = null;

        int projIdx = prompt.indexOf("--- PROJECTS ---");
        int eduIdx = prompt.indexOf("--- EDUCATION ---");
        String projectsSection = "";
        if (projIdx != -1) {
            projectsSection = eduIdx != -1 ? prompt.substring(projIdx, eduIdx) : prompt.substring(projIdx);
        }

        if (projectsSection.contains("No projects listed")) {
            projectWeak = true;
            projectScore = 30;
            weakProjectText = "No projects listed";
            weakProjectReason = "Profile has no technical projects listed.";
        } else {
            boolean hasTech = projectsSection.contains("Technologies:");
            boolean hasBullets = projectsSection.contains("Bullet Points:") || projectsSection.contains("* ");
            boolean hasMetrics = projectsSection.matches("(?s).*\\b(\\d+%|\\d+ms|\\d+k|\\d+x|\\d+\\s*users|\\d+\\s*rps|scale|reduced|optimized|improved|sub-50ms)\\b.*");
            boolean hasLinks = projectsSection.contains("GitHub/Repo:") || projectsSection.contains("Live URL:");

            if (!hasTech || !hasBullets || !hasMetrics) {
                projectWeak = true;
                projectScore = 48;
                weakProjectText = extractField(projectsSection, "Project:");
                if (weakProjectText.isEmpty()) weakProjectText = "Project";
                StringBuilder rsb = new StringBuilder("Project lacks ");
                List<String> missing = new ArrayList<>();
                if (!hasTech) missing.add("defined technologies");
                if (!hasBullets) missing.add("bullet points");
                if (!hasMetrics) missing.add("measurable outcome metrics");
                rsb.append(String.join(", ", missing)).append(".");
                weakProjectReason = rsb.toString();
            } else if (!hasLinks) {
                projectScore = 78;
            }
        }

        // Skills & Education
        int skillsScore = prompt.contains("--- SKILLS ---") && !prompt.contains("No skills listed") ? 74 : 45;
        int educationScore = prompt.contains("--- EDUCATION ---") && !prompt.contains("No education listed") ? 82 : 50;

        // Ensure sections do not all receive similar scores
        if (headlineScore == aboutScore) headlineScore = Math.max(25, headlineScore - 4);
        if (aboutScore == projectScore) aboutScore = Math.max(30, aboutScore - 3);
        if (skillsScore == educationScore) skillsScore = Math.max(40, skillsScore - 5);

        // Overall score:
        int overallScore;
        if (!headlineWeak && !aboutWeak && !projectWeak && headlineScore >= 85 && aboutScore >= 85 && projectScore >= 85) {
            overallScore = 85;
        } else {
            int calc = (int) Math.round((headlineScore * 0.2) + (aboutScore * 0.2) + (projectScore * 0.3) + (skillsScore * 0.15) + (educationScore * 0.15));
            if (headlineWeak || aboutWeak || projectWeak) {
                overallScore = Math.min(55, Math.max(35, calc));
            } else {
                overallScore = Math.min(84, calc);
            }
        }

        // Build weaknesses list
        List<String> weaknessesJson = new ArrayList<>();
        if (headlineWeak) {
            weaknessesJson.add(String.format("""
                {
                  "section": "HEADLINE",
                  "weakText": "%s",
                  "reason": "%s",
                  "improvementSuggestion": "Specify your target role and 2-3 core technologies."
                }""", escapeJson(headline), escapeJson(weakHeadlineReason)));
        }
        if (aboutWeak) {
            weaknessesJson.add(String.format("""
                {
                  "section": "ABOUT",
                  "weakText": "%s",
                  "reason": "%s",
                  "improvementSuggestion": "Expand your about section with 3-4 professional sentences describing technical passion and engineering focus."
                }""", escapeJson(about), escapeJson(weakAboutReason)));
        }
        if (projectWeak && weakProjectText != null) {
            weaknessesJson.add(String.format("""
                {
                  "section": "PROJECTS",
                  "weakText": "%s",
                  "reason": "%s",
                  "improvementSuggestion": "Add tech stack, architecture bullets, and quantify outcomes with measurable impact."
                }""", escapeJson(weakProjectText), escapeJson(weakProjectReason)));
        }

        if (!projectsSection.contains("GitHub/Repo:") && !projectsSection.contains("Live URL:") && !projectsSection.contains("No projects listed")) {
            weaknessesJson.add("""
                {
                  "section": "PROJECTS",
                  "weakText": "Missing repository links",
                  "reason": "Projects do not have public GitHub repository links to verify code quality.",
                  "improvementSuggestion": "Link your GitHub repository to enable automated verification."
                }""");
        }

        if (weaknessesJson.isEmpty()) {
            weaknessesJson.add("""
                {
                  "section": "PROJECTS",
                  "weakText": "Quantifiable outcomes",
                  "reason": "Could further emphasize unit test coverage percentage.",
                  "improvementSuggestion": "Add code coverage badges or metrics."
                }""");
        }

        return String.format("""
            {
              "overallScore": %d,
              "sectionScores": {
                "headline": %d,
                "about": %d,
                "skills": %d,
                "projects": %d,
                "education": %d
              },
              "weaknesses": [
                %s
              ],
              "strengths": [
                "Technical foundation is aligned with entry-level standards"
              ],
              "keyRecommendations": [
                "Quantify all project outcomes and provide public repository links"
              ]
            }""",
            overallScore, headlineScore, aboutScore, skillsScore, projectScore, educationScore,
            String.join(",\n", weaknessesJson)
        );
    }

    private static String extractField(String text, String prefix) {
        int idx = text.indexOf(prefix);
        if (idx == -1) return "";
        int start = idx + prefix.length();
        int end = text.indexOf('\n', start);
        if (end == -1) end = text.length();
        return text.substring(start, end).trim();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
