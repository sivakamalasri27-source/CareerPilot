package com.careerpilot.ai.client;

import lombok.Getter;

@Getter
public class GeminiApiException extends RuntimeException {

    private final int statusCode;
    /** Raw response body from Gemini — may be null for non-HTTP errors. Never contains the API key. */
    private final String responseBody;

    public GeminiApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = null;
    }

    public GeminiApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public GeminiApiException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = null;
    }

    public GeminiApiException(String message, Throwable cause, int statusCode, String responseBody) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
