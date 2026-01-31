package io.oxyjen.llm.schema;

/**
 * Thrown when schema enforcement fails after all retries.
 */
public class SchemaException extends RuntimeException {

    private final String lastResponse;

    public SchemaException(String message, String lastResponse) {
        super(message);
        this.lastResponse = lastResponse;
    }

    public SchemaException(String message, String lastResponse, Throwable cause) {
        super(message, cause);
        this.lastResponse = lastResponse;
    }

    /**
     * Raw LLM response from the final attempt.
     */
    public String lastResponse() {
        return lastResponse;
    }

    @Override
    public String getMessage() {
        return super.getMessage() +
               "\n\nLast model response:\n" +
               lastResponse;
    }
}
