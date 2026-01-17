package io.oxyjen.llm.exceptions;

/**
 * transient
 * retryable
 * provider may be down
 */
public final class NetworkException extends LLMException {
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
