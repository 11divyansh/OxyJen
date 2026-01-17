package io.oxyjen.llm.exceptions;

/**
 * transient failure
 * retryable
 * backoff applies
 */
public final class RateLimitException extends LLMException {
    public RateLimitException(String message) {
        super(message);
    }
}