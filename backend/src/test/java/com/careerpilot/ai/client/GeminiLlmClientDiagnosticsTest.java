package com.careerpilot.ai.client;

import com.careerpilot.ai.config.LlmProperties;
import com.careerpilot.ai.dto.LlmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GeminiLlmClientDiagnosticsTest {

    private static final String OK_BODY = """
            {
              "candidates": [{
                "content": { "parts": [{ "text": "{\\"score\\": 85}" }] }
              }]
            }
            """;

    private ObjectMapper objectMapper;
    private LlmProperties properties;
    private MockRestServiceServer mockServer;
    private GeminiLlmClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new LlmProperties();
        properties.setProvider("gemini");
        properties.setApiKey("AIzaSySecretApiKey12345");
        properties.setModel("gemini-flash-latest");

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiLlmClient(properties, objectMapper, builder.build());
    }

    @Test
    @DisplayName("Sends Accept: application/json, handles application/octet-stream response cleanly")
    void sendsAcceptHeaderAndHandlesOctetStreamResponse() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("Test"))
                .andExpect(jsonPath("$.contents[0].role").doesNotExist())
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_OCTET_STREAM).body(OK_BODY));

        var response = client.generate(LlmRequest.builder().prompt("Test").build());
        assertThat(response.getContent()).contains("score");
        mockServer.verify();
    }

    @Test
    @DisplayName("401 throws immediately — no fallback attempted")
    void handles401Unauthorized() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Test").build()))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("Gemini rejected the API key")
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 401);

        mockServer.verify();   // exactly 1 request — no fallback
    }

    @Test
    @DisplayName("403 throws immediately — no fallback attempted")
    void handles403Forbidden() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Test").build()))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("Gemini rejected the API key")
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 403);

        mockServer.verify();   // exactly 1 request — no fallback
    }

    @Test
    @DisplayName("429 throws immediately — no fallback attempted")
    void handles429QuotaExceeded() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Test").build()))
                .isInstanceOf(GeminiApiException.class)
                .hasMessageContaining("Gemini API quota exceeded")
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 429);

        mockServer.verify();   // exactly 1 request — no fallback
    }

    @Test
    @DisplayName("400 throws immediately without fallback — fallback only on 404")
    void handles400WithoutFallback() {
        String errorBody = "{\"error\":{\"code\":400,\"message\":\"Invalid request format\"}}";

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON).body(errorBody));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Test").build()))
                .isInstanceOf(GeminiApiException.class)
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 400)
                .hasMessageContaining("Invalid request format");

        mockServer.verify(); // exactly 1 request — NO fallback on 400
    }

    @Test
    @DisplayName("404 on primary model falls back to next in list and succeeds")
    void handles404WithFallbackSuccess() {
        // Primary model (gemini-flash-latest) -> 404
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // First fallback -> gemini-3.5-flash -> 200
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(OK_BODY));

        var response = client.generate(LlmRequest.builder().prompt("Test").build());
        assertThat(response.getContent()).contains("score");
        assertThat(response.getModelName()).isEqualTo("gemini-3.5-flash");
        mockServer.verify();
    }

    @Test
    @DisplayName("All fallbacks exhausted on 404 throws GeminiApiException(503)")
    void handles404AllFallbacksExhausted() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Test").build()))
                .isInstanceOf(GeminiApiException.class)
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 503)
                .hasMessageContaining("All Gemini model fallbacks failed");

        mockServer.verify();
    }

    @Test
    @DisplayName("Working model is remembered — subsequent call uses it directly")
    void remembersWorkingModel() {
        // First call: primary 404, fallback gemini-3.5-flash succeeds
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(OK_BODY));

        // Second call: should go straight to gemini-3.5-flash (no retry on primary)
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(OK_BODY));

        client.generate(LlmRequest.builder().prompt("First").build());
        client.generate(LlmRequest.builder().prompt("Second").build());

        mockServer.verify();  // exactly 3 requests total
    }

    @Test
    @DisplayName("Blocked by promptFeedback throws clear error")
    void handlesPromptFeedbackBlocked() {
        String blockedBody = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(blockedBody));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Blocked").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("promptFeedback.blockReason=SAFETY");

        mockServer.verify();
    }

    @Test
    @DisplayName("Empty candidates or finishReason without content throws clear error")
    void handlesFinishReasonBlocked() {
        String finishReasonBody = "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}";

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(finishReasonBody));

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Blocked").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("finishReason=SAFETY");

        mockServer.verify();
    }

    @Test
    @DisplayName("Maps SocketTimeoutException to Gemini timed out, please retry")
    void handlesSocketTimeoutException() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("Read timed out", new java.net.SocketTimeoutException("Read timed out"));
                });

        assertThatThrownBy(() -> client.generate(LlmRequest.builder().prompt("Timeout").build()))
                .isInstanceOf(GeminiApiException.class)
                .hasMessage("Gemini timed out, please retry")
                .matches(e -> ((GeminiApiException) e).getStatusCode() == 504);

        mockServer.verify();
    }

    @Test
    @DisplayName("Ignores gemini-2.5-flash and uses gemini-flash-latest")
    void ignoresGemini25Flash() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=AIzaSySecretApiKey12345"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(OK_BODY));

        var response = client.generate(LlmRequest.builder().prompt("Test").model("gemini-2.5-flash").build());
        assertThat(response.getContent()).contains("score");
        mockServer.verify();
    }
}
