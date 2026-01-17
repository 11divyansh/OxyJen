package io.oxyjen.llm.exceptions;

/**
 * request took too long
 * retryable
 * may need different timeout or fallback
 */
public final class TimeoutException extends LLMException {
    public TimeoutException(String message) {
        super(message);
    }
}
