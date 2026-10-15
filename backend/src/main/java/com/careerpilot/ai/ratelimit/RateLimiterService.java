package com.careerpilot.ai.ratelimit;

import com.careerpilot.ai.config.LlmProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final LlmProperties properties;
    private final Map<Long, Bucket> userBuckets = new ConcurrentHashMap<>();

    /**
     * Checks if the user is within rate limits and consumes 1 token.
     * Throws RateLimitExceededException if limit is exceeded.
     */
    public void consumeOrThrow(Long userId) {
        Bucket bucket = userBuckets.computeIfAbsent(userId, this::createNewBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (!probe.isConsumed()) {
            long waitForRefillNanos = probe.getNanosToWaitForRefill();
            long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitForRefillNanos));
            log.warn("Rate limit exceeded for user {}. Retry after {} seconds", userId, retryAfterSeconds);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    public void resetForUser(Long userId) {
        userBuckets.remove(userId);
    }

    public void clearAll() {
        userBuckets.clear();
    }

    private Bucket createNewBucket(Long userId) {
        LlmProperties.RateLimit rateLimit = properties.getRateLimit();
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimit.getCapacity())
                .refillGreedy(rateLimit.getRefillTokens(), Duration.ofSeconds(rateLimit.getRefillDurationSeconds()))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
