package com.careerpilot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DotenvEnvironmentPostProcessorTest {

    @Test
    @DisplayName("Loads .env file with comments, whitespace, and quoted values")
    void loadsDotenvFileCleanly(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve(".env").toFile();
        String content = """
                # Comment line
                ! Another comment
                GEMINI_API_KEY = "AIzaSyTestKey123"
                LLM_PROVIDER = 'gemini'
                LLM_MODEL = gemini-flash-latest
                EMPTY_LINE =
                
                ANOTHER_KEY=value_with_spaces   
                """;
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }

        Map<String, Object> props = DotenvEnvironmentPostProcessor.loadDotenvFile(file);

        assertThat(props).containsEntry("GEMINI_API_KEY", "AIzaSyTestKey123");
        assertThat(props).containsEntry("LLM_PROVIDER", "gemini");
        assertThat(props).containsEntry("LLM_MODEL", "gemini-flash-latest");
        assertThat(props).containsEntry("ANOTHER_KEY", "value_with_spaces");
    }

    @Test
    @DisplayName("Strips UTF-8 BOM from the first line")
    void stripsUtf8Bom(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve(".env").toFile();
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "GEMINI_API_KEY=AIzaSyBomTestKey\n".getBytes(StandardCharsets.UTF_8);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bom);
            fos.write(text);
        }

        Map<String, Object> props = DotenvEnvironmentPostProcessor.loadDotenvFile(file);
        assertThat(props).containsEntry("GEMINI_API_KEY", "AIzaSyBomTestKey");
    }

    @Test
    @DisplayName("Missing file returns empty map without throwing exception")
    void missingFileReturnsEmptyMap() {
        File missing = new File("non_existent_folder_xyz/.env");
        Map<String, Object> props = DotenvEnvironmentPostProcessor.loadDotenvFile(missing);
        assertThat(props).isEmpty();
    }

    @Test
    @DisplayName("Sanitize value trims whitespace, quotes, and BOM")
    void sanitizeValueHandlesAllCases() {
        assertThat(DotenvEnvironmentPostProcessor.sanitizeValue("  \"hello world\"  ")).isEqualTo("hello world");
        assertThat(DotenvEnvironmentPostProcessor.sanitizeValue("  'single quote'  ")).isEqualTo("single quote");
        assertThat(DotenvEnvironmentPostProcessor.sanitizeValue("\uFEFFkey_with_bom")).isEqualTo("key_with_bom");
        assertThat(DotenvEnvironmentPostProcessor.sanitizeValue(null)).isEmpty();
    }

    @Test
    @DisplayName("Post processor injects properties into Spring StandardEnvironment")
    void postProcessEnvironmentInjectsProperties(@TempDir Path tempDir) throws Exception {
        DotenvEnvironmentPostProcessor processor = new DotenvEnvironmentPostProcessor();
        StandardEnvironment environment = new StandardEnvironment();
        SpringApplication application = new SpringApplication();

        // Run postProcessEnvironment
        processor.postProcessEnvironment(environment, application);

        // Verify property source is present
        assertThat(environment.getPropertySources().contains(DotenvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
    }
}
