package io.oxyjen.llm.models;

/**
 * Token usage information.
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {}
