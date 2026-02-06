package io.oxyjen.llm.models;

/**
 * Token usage information.
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
	public TokenUsage {
        if (promptTokens < 0) {
            throw new IllegalArgumentException("promptTokens cannot be negative");
        }
        if (completionTokens < 0) {
            throw new IllegalArgumentException("completionTokens cannot be negative");
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("totalTokens cannot be negative");
        }
        if (totalTokens != promptTokens + completionTokens) {
            throw new IllegalArgumentException(
                "totalTokens must equal promptTokens + completionTokens"
            );
        }
    }
}
