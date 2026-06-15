package io.oxyjen.llm.exceptions;

/**
 * transient failure
 * retryable
 * backoff applies
 */
public final class RateLimitException extends LLMException {
	private final long retryAfterMs; 
    public RateLimitException(String message, long retryAfterMs) {
        super(message);
        this.retryAfterMs =retryAfterMs;
    }
    public long getRetryAfterMs() { return retryAfterMs;}
}