package io.oxyjen.execution.metrics;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Typed metrics captured for a single node execution attempt.
 *
 * <p>A typed record means every exporter, store, and replay consumer reads the same fields without
 * needing to agree on string keys and adding a field later is a compile-time
 * change everyone sees, not a silent contract.
 *
 * <p>All fields are nullable/boxed because not every node type produces every
 * metric — a {@code BranchNode} has no token counts, a non-LLM node has no
 * cost. Consumers should treat {@code null} as "not applicable," not "zero."
 *
 * @param promptTokens      input tokens consumed, if this node called an LLM
 * @param completionTokens  output tokens produced, if this node called an LLM
 * @param costUsd           estimated cost in USD, if cost tracking is enabled
 * @param schemaValid       whether structured output passed schema validation, if applicable
 * @param provider          name of the LLM provider used (e.g. "openai", "gemini"), if applicable
 * @param model             specific model identifier used, if applicable
 */
public record NodeMetrics(
		Duration duration,
        Long promptTokens,
        Long completionTokens,
        BigDecimal costUsd,
        Boolean outputValid,
        String provider,
        String model,
        Integer retryCount
) {

    /** An empty metrics instance for nodes that produce no measurable metrics. */
    public static final NodeMetrics NONE = new NodeMetrics(null, null, null, null, null, null, null, null);

    /** Total tokens (prompt + completion), or {@code null} if neither is present. */
    public Long totalTokens() {
        if (promptTokens == null && completionTokens == null) return null;
        return (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens);
    }
}