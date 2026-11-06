package com.careerpilot.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pure unit tests for {@link AiConfig#validateGeminiConfiguration(LlmProperties)}.
 * No Spring context is needed — we test the validation logic directly.
 */
class GeminiConfigValidationTest {

    @Test
    @DisplayName("Missing or blank key throws IllegalStateException for gemini provider")
    void missingKeyFailsStartupWhenGemini() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");

        // null key
        props.setApiKey(null);
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured or blank");

        // empty string key
        props.setApiKey("");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured or blank");

        // whitespace-only key
        props.setApiKey("   ");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured or blank");
    }

    @Test
    @DisplayName("Placeholder key throws IllegalStateException for gemini provider")
    void placeholderKeyFailsStartupWhenGemini() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");

        props.setApiKey("your_gemini_api_key_here");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");

        props.setApiKey("your_api_key_here");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");

        props.setApiKey("<your_api_key>");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");

        props.setApiKey("mock-key");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");

        props.setApiKey("changeme");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");

        props.setApiKey("dummy");
        assertThatThrownBy(() -> AiConfig.validateGeminiConfiguration(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured with placeholder value");
    }

    @Test
    @DisplayName("Valid API key passes validation without throwing")
    void validKeyPassesValidation() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");
        props.setApiKey("AIzaSyTestValidKey1234567890");
        props.setModel("gemini-2.5-flash");

        assertThatCode(() -> AiConfig.validateGeminiConfiguration(props))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Placeholder model is normalized to gemini-2.5-flash")
    void placeholderModelIsNormalized() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");
        props.setApiKey("AIzaSyValidDummyKey");
        props.setModel("<Gemini 3.8 Flash>");

        AiConfig.validateGeminiConfiguration(props);
        assertThat(props.getModel()).isEqualTo("gemini-flash-latest");
    }

    @Test
    @DisplayName("Empty model is normalized to gemini-2.5-flash")
    void emptyModelIsNormalized() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");
        props.setApiKey("AIzaSyValidDummyKey");
        props.setModel("");

        AiConfig.validateGeminiConfiguration(props);
        assertThat(props.getModel()).isEqualTo("gemini-flash-latest");
    }

    @Test
    @DisplayName("Null model is normalized to gemini-2.5-flash")
    void nullModelIsNormalized() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");
        props.setApiKey("AIzaSyValidDummyKey");
        props.setModel(null);

        AiConfig.validateGeminiConfiguration(props);
        assertThat(props.getModel()).isEqualTo("gemini-flash-latest");
    }

    @Test
    @DisplayName("API key with quotes and spaces is trimmed properly")
    void keyWithQuotesAndSpacesIsTrimmed() {
        LlmProperties props = new LlmProperties();
        props.setProvider("gemini");
        props.setApiKey("  \"AIzaSyValidDummyKeyWithQuotes\"  ");

        assertThat(props.getApiKey()).isEqualTo("AIzaSyValidDummyKeyWithQuotes");
        assertThatCode(() -> AiConfig.validateGeminiConfiguration(props))
                .doesNotThrowAnyException();
    }
}
