package com.careerpilot.tool.github;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GitHubTool {

    private static final String GITHUB_REPOS_URL = "https://api.github.com/users/{username}/repos?per_page=100&type=public&sort=pushed";
    private static final String GITHUB_FILE_CONTENTS_URL = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";

    private final RestTemplate restTemplate;
    private final String githubToken;
    private final Cache<String, List<GitHubRepoDto>> repoCache;
    private final Cache<String, Boolean> fileCache;

    @org.springframework.beans.factory.annotation.Autowired
    public GitHubTool(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${github.token:${GITHUB_TOKEN:}}") String githubToken,
            @Value("${github.cache.ttl-minutes:15}") int cacheTtlMinutes
    ) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.githubToken = githubToken;
        this.repoCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
        this.fileCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    /**
     * Testing constructor allowing custom RestTemplate and Cache injection.
     */
    public GitHubTool(RestTemplate restTemplate, String githubToken) {
        this.restTemplate = restTemplate;
        this.githubToken = githubToken;
        this.repoCache = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
        this.fileCache = Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(500)
                .build();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        headers.set(HttpHeaders.USER_AGENT, "CareerPilot-Agent");
        if (githubToken != null && !githubToken.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken.trim());
        }
        return headers;
    }

    /**
     * Fetches public non-fork repositories for the given GitHub username.
     * Caches results with Caffeine. Handles 404, 403, 429, and timeouts gracefully.
     */
    public List<GitHubRepoDto> fetchPublicRepositories(String username) {
        if (username == null || username.trim().isBlank()) {
            return Collections.emptyList();
        }

        String normalizedUsername = username.trim().toLowerCase();

        List<GitHubRepoDto> cached = repoCache.getIfPresent(normalizedUsername);
        if (cached != null) {
            log.debug("GitHub cache HIT for user: {}", normalizedUsername);
            return cached;
        }

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<List<GitHubRepoDto>> response = restTemplate.exchange(
                    GITHUB_REPOS_URL,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<GitHubRepoDto>>() {},
                    normalizedUsername
            );

            List<GitHubRepoDto> repos = response.getBody();
            if (repos == null) {
                repos = Collections.emptyList();
            }

            // Ignore forks by default
            List<GitHubRepoDto> nonForks = repos.stream()
                    .filter(repo -> repo != null && !Boolean.TRUE.equals(repo.getFork()))
                    .toList();

            repoCache.put(normalizedUsername, nonForks);
            log.info("Fetched {} public non-fork repositories for GitHub user: {}", nonForks.size(), normalizedUsername);
            return nonForks;

        } catch (HttpClientErrorException.NotFound nfe) {
            log.warn("GitHub user not found: {}", normalizedUsername);
            List<GitHubRepoDto> emptyList = Collections.emptyList();
            repoCache.put(normalizedUsername, emptyList);
            return emptyList;

        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.TooManyRequests rle) {
            log.warn("GitHub rate limit reached while querying user {}: {}", normalizedUsername, rle.getMessage());
            return Collections.emptyList();

        } catch (ResourceAccessException rae) {
            log.warn("GitHub request timed out for user {}: {}", normalizedUsername, rae.getMessage());
            return Collections.emptyList();

        } catch (HttpServerErrorException hse) {
            log.warn("GitHub server error for user {}: {}", normalizedUsername, hse.getMessage());
            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Unexpected error fetching GitHub repositories for user {}: {}", normalizedUsername, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Checks if a specific file or path exists in a user's repository.
     */
    public boolean checkFileExists(String username, String repoName, String filePath) {
        if (username == null || repoName == null || filePath == null || filePath.isBlank()) {
            return false;
        }

        String normalizedUsername = username.trim().toLowerCase();
        String normalizedRepo = repoName.trim().toLowerCase();
        String normalizedPath = filePath.trim();
        String cacheKey = normalizedUsername + "/" + normalizedRepo + "/" + normalizedPath.toLowerCase();

        Boolean cached = fileCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_FILE_CONTENTS_URL,
                    HttpMethod.GET,
                    entity,
                    String.class,
                    normalizedUsername,
                    repoName,
                    normalizedPath
            );
            boolean exists = response.getStatusCode().is2xxSuccessful();
            fileCache.put(cacheKey, exists);
            return exists;
        } catch (HttpClientErrorException.NotFound nfe) {
            fileCache.put(cacheKey, false);
            return false;
        } catch (Exception e) {
            log.warn("Error checking file {} in {}/{}: {}", normalizedPath, normalizedUsername, repoName, e.getMessage());
            return false;
        }
    }

    /**
     * Searches public repositories for a repository containing the given file hint.
     * Returns the web link to the verified file / repo if found.
     */
    public Optional<String> findRepoWithFile(String username, String fileHint) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        List<GitHubRepoDto> repos = fetchPublicRepositories(username);
        if (repos == null || repos.isEmpty()) {
            return Optional.empty();
        }

        String targetFile = (fileHint != null && !fileHint.isBlank()) ? fileHint.trim() : "README.md";

        for (GitHubRepoDto repo : repos) {
            if (repo != null && repo.getName() != null) {
                if (checkFileExists(username, repo.getName(), targetFile)) {
                    String fileUrl = (repo.getHtmlUrl() != null)
                            ? repo.getHtmlUrl() + "/blob/main/" + targetFile
                            : "https://github.com/" + username + "/" + repo.getName();
                    return Optional.of(fileUrl);
                }
            }
        }
        return Optional.empty();
    }

    public void clearCache() {
        repoCache.invalidateAll();
        fileCache.invalidateAll();
    }
}
