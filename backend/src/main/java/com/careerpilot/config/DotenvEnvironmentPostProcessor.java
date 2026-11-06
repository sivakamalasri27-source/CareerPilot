package com.careerpilot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loads .env file automatically in development and local environments into the Spring Environment.
 * Resolution precedence:
 * 1. Real OS environment variables (highest priority)
 * 2. backend/.env (working directory or relative to classes/jar location)
 * 3. ../.env or root .env
 *
 * Strips UTF-8 BOM, whitespace, wrapping quotes, and ignores comments and blank lines.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);
    public static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Skip in test profile so tests using mock/fake LLM provider are not overridden by .env or OS env
        if (isTestProfileOrEnvironment(environment)) {
            Map<String, Object> testProps = new HashMap<>();
            testProps.put("llm.provider", "fake");
            testProps.put("LLM_PROVIDER", "fake");
            testProps.put("llm.api-key", "test-api-key");
            environment.getPropertySources().addFirst(new MapPropertySource("testForcedFakeLlmProperties", testProps));
            return;
        }

        File dotenvFile = findDotenvFile();
        Map<String, Object> props = new HashMap<>();

        String foundPath = "none";
        boolean fileFound = false;

        if (dotenvFile != null && dotenvFile.exists() && dotenvFile.isFile()) {
            fileFound = true;
            foundPath = dotenvFile.getAbsolutePath();
            props = loadDotenvFile(dotenvFile);
        }

        // Determine if key is present from OS env or .env
        String geminiKey = environment.getProperty("GEMINI_API_KEY");
        String llmKey = environment.getProperty("LLM_API_KEY");
        String osKey = (geminiKey != null && !geminiKey.isBlank()) ? geminiKey : llmKey;

        String source = "none";
        String effectiveKey = null;

        if (osKey != null && !osKey.isBlank()) {
            source = "env";
            effectiveKey = sanitizeValue(osKey);
        } else if (fileFound) {
            Object dotKey = props.get("GEMINI_API_KEY");
            if (dotKey == null || dotKey.toString().isBlank()) {
                dotKey = props.get("LLM_API_KEY");
            }
            if (dotKey != null && !dotKey.toString().isBlank()) {
                source = ".env";
                effectiveKey = sanitizeValue(dotKey.toString());
            }
        }

        boolean keyPresent = effectiveKey != null && !effectiveKey.isBlank();
        int keyLength = keyPresent ? effectiveKey.length() : 0;

        // Startup log (never the full key)
        log.info(".env found at {}: {}, key present: {}, key length: {}, source: {}",
                foundPath, fileFound ? "yes" : "no", keyPresent ? "yes" : "no", keyLength, source);

        if (fileFound && !props.isEmpty()) {
            // Also normalize aliases for Spring configuration
            if (props.containsKey("GEMINI_API_KEY") && !props.containsKey("llm.api-key")) {
                props.put("llm.api-key", props.get("GEMINI_API_KEY"));
            } else if (props.containsKey("LLM_API_KEY") && !props.containsKey("llm.api-key")) {
                props.put("llm.api-key", props.get("LLM_API_KEY"));
            }
            if (props.containsKey("LLM_PROVIDER") && !props.containsKey("llm.provider")) {
                props.put("llm.provider", props.get("LLM_PROVIDER"));
            }
            if (props.containsKey("LLM_MODEL") && !props.containsKey("llm.model")) {
                props.put("llm.model", props.get("LLM_MODEL"));
            }

            // Register property source after system environment so OS env vars take precedence
            if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new MapPropertySource(PROPERTY_SOURCE_NAME, props)
                );
            } else {
                environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
            }
        }
    }

    public static File findDotenvFile() {
        Set<Path> candidates = new LinkedHashSet<>();

        // 1. From current working directory
        Path cwd = Paths.get("").toAbsolutePath();
        candidates.add(cwd.resolve("backend").resolve(".env"));
        candidates.add(cwd.resolve(".env"));
        candidates.add(cwd.resolve("..").resolve(".env"));
        candidates.add(cwd.resolve("..").resolve("backend").resolve(".env"));

        // 2. From class / jar code location
        try {
            URL location = DotenvEnvironmentPostProcessor.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path classPath = Paths.get(location.toURI()).toAbsolutePath();
                Path dir = classPath.toFile().isDirectory() ? classPath : classPath.getParent();
                if (dir != null) {
                    candidates.add(dir.resolve(".env"));
                    candidates.add(dir.resolve("..").resolve(".env"));
                    candidates.add(dir.resolve("..").resolve("..").resolve(".env"));
                    candidates.add(dir.resolve("..").resolve("..").resolve("backend").resolve(".env"));
                    candidates.add(dir.resolve("backend").resolve(".env"));
                }
            }
        } catch (Exception ignored) {
        }

        for (Path path : candidates) {
            try {
                Path normalized = path.normalize();
                File file = normalized.toFile();
                if (file.exists() && file.isFile() && file.canRead()) {
                    return file;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Map<String, Object> loadDotenvFile(File file) {
        Map<String, Object> props = new HashMap<>();
        if (file == null || !file.exists() || !file.isFile()) {
            return props;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    // Strip UTF-8 BOM if present
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                }
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    value = sanitizeValue(value);
                    props.put(key, value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read .env file at {}: {}", file.getAbsolutePath(), e.getMessage());
        }
        return props;
    }

    public static String sanitizeValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1).trim();
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2)
                || (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2)) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isTestProfileOrEnvironment(ConfigurableEnvironment environment) {
        if (environment != null) {
            if ("true".equalsIgnoreCase(environment.getProperty("careerpilot.is-test"))) {
                return true;
            }
            for (String profile : environment.getActiveProfiles()) {
                if ("test".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
            for (String profile : environment.getDefaultProfiles()) {
                if ("test".equalsIgnoreCase(profile)) {
                    return true;
                }
            }
            String activeProp = environment.getProperty("spring.profiles.active");
            if (activeProp != null && activeProp.toLowerCase().contains("test")) {
                return true;
            }
            try {
                if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("test"))) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        String sysProp = System.getProperty("spring.profiles.active");
        if (sysProp != null && sysProp.toLowerCase().contains("test")) {
            return true;
        }
        return false;
    }
}
