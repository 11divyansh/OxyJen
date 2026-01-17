package io.oxyjen.llm.exceptions;

public final class TokenLimitExceededException extends LLMException {

    private final int contextLimit;

    public TokenLimitExceededException(String message, int contextLimit) {
        super(message);
        this.contextLimit = contextLimit;
    }

    public int getContextLimit() {
        return contextLimit;
    }
}