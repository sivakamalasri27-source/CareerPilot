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

    @Getter
    private final List<LlmRequest> recordedRequests = Collections.synchronizedList(new ArrayList<>());
    private final Queue<String> responseQueue = new LinkedList<>();

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

        String content;
        synchronized (responseQueue) {
            if (!responseQueue.isEmpty()) {
                content = responseQueue.poll();
            } else {
                content = defaultResponse;
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

    public void reset() {
        recordedRequests.clear();
        synchronized (responseQueue) {
            responseQueue.clear();
        }
        this.defaultResponse = DEFAULT_CANNED_RESPONSE;
        this.simulateTimeout = false;
        this.simulateServerError = false;
        this.simulateClientError = false;
    }
}
