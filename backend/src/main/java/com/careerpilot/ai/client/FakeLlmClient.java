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

    @Override
    public String getProviderName() {
        return "fake";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        recordedRequests.add(request);

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

        // Check if targeted queue exists for Auditor or Recruiter
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
            }
        }

        if (content == null) {
            synchronized (responseQueue) {
                if (!responseQueue.isEmpty()) {
                    content = responseQueue.poll();
                } else if (!DEFAULT_CANNED_RESPONSE.equals(defaultResponse)) {
                    content = defaultResponse;
                } else {
                    if (prompt != null && (prompt.contains("ProfileAuditorAgent") || prompt.contains("overallScore"))) {
                        content = CANNED_AUDITOR_RESPONSE;
                    } else if (prompt != null && (prompt.contains("RecruiterSimulatorAgent") || prompt.contains("MNC_HR") || prompt.contains("hiring committee"))) {
                        content = CANNED_RECRUITER_RESPONSE;
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
    }
}
