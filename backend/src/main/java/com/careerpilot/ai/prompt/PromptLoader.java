package com.careerpilot.ai.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PromptLoader {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
    private static final String PROMPT_DIRECTORY = "prompts/";

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * Loads a prompt template by filename and substitutes named placeholders.
     * Fails fast if any placeholder in the template is not provided in variables.
     *
     * @param promptFileName filename under classpath prompts/, e.g. "sample-audit.v1.txt"
     * @param variables      map of placeholder names to replacement values
     * @return rendered prompt string
     */
    public String render(String promptFileName, Map<String, String> variables) {
        String template = getRawTemplate(promptFileName);
        Set<String> requiredPlaceholders = extractPlaceholders(template);

        List<String> missing = new ArrayList<>();
        for (String placeholder : requiredPlaceholders) {
            if (variables == null || !variables.containsKey(placeholder) || variables.get(placeholder) == null) {
                missing.add(placeholder);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required prompt placeholder(s) in '" + promptFileName + "': " + String.join(", ", missing));
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = variables.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Reads raw template content from classpath.
     */
    public String getRawTemplate(String promptFileName) {
        return templateCache.computeIfAbsent(promptFileName, name -> {
            String path = PROMPT_DIRECTORY + name;
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new IllegalArgumentException("Prompt template not found at classpath:" + path);
            }
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read prompt template: " + path, e);
            }
        });
    }

    /**
     * Extracts placeholder variable names from a template string.
     */
    public Set<String> extractPlaceholders(String template) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    /**
     * Helper to extract the version tag from filename (e.g. "sample-audit.v1.txt" -> "v1").
     */
    public String extractVersion(String promptFileName) {
        int dotIdx = promptFileName.indexOf('.');
        int nextDotIdx = promptFileName.indexOf('.', dotIdx + 1);
        if (dotIdx != -1 && nextDotIdx != -1) {
            return promptFileName.substring(dotIdx + 1, nextDotIdx);
        }
        return "v1";
    }
}
