package io.oxyjen.llm.exceptions;

/**
 * permanent failure
 * retrying is useless
 */
public final class InvalidAPIKeyException extends LLMException {
    public InvalidAPIKeyException(String message) {
        super(message);
    }
}
