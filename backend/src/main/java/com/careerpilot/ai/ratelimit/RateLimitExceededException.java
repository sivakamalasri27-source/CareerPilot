package com.careerpilot.ai.ratelimit;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("AI rate limit exceeded. Please wait before making more requests.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
