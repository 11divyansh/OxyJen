package io.oxyjen.llm.exceptions;

/**
 * Thrown when an LLM call exceeds its execution timeout.
 * retryable(transient)
 * may need different timeout or fallback
 */
public final class TimeoutException extends LLMException {
    public TimeoutException(String message) {
        super(message);
    }
}
