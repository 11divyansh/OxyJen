package io.oxyjen.execution.metrics;

import java.math.BigDecimal;
import java.time.Duration;

import io.oxyjen.llm.models.ModelInfo;

/**
 * Typed metrics captured for a single node execution attempt.
 *
 * <p>A typed record means every exporter, store, and replay consumer reads the same fields without
 * needing to agree on string keys and adding a field later is a compile-time
 * change everyone sees, not a silent contract.
 *
 * <p>All fields are nullable/boxed because not every node type produces every
 * metric, a {@code BranchNode} has no token counts, a non-LLM node has no
 * cost. Consumers should treat {@code null} as "not applicable," not "zero."
 * 
 * * <p>Kept deliberately minimal for now: only {@link BasicNodeMetrics} (any
 * generic graph node) and {@link LlmNodeMetrics} (LLM calls, including tool
 * use) exist, since those are the only node categories OxyJen currently has.
 * Variants for other node families (HTTP, database, etc.) are intentionally
 * left out until those node types actually exist.
 *
 * <p>Every variant carries {@link #duration()} since latency is universal —
 * every exporter, dashboard, and replay consumer wants it regardless of node
 * type.
 *
 */
public sealed interface NodeMetrics {
	/** How long this node attempt took to execute. */
	Duration duration();
    /** An empty metrics instance for nodes that produce no measurable metrics. */
	/**
     * Generic metrics for any non-LLM graph node.
     */
    record GraphNodeMetrics(Duration duration) implements NodeMetrics {

        public static GraphNodeMetrics of(Duration duration) {
            return new GraphNodeMetrics(duration);
        }
    }

    /**
     * Metrics for a node that made one or more LLM calls, including tool use.
     *
     * @param duration          execution time
     * @param promptTokens      input tokens consumed
     * @param completionTokens  output tokens produced
     * @param costMicros        cost in micros of one US dollar
     * @param modelInfo         model that handled the request
     * @param outputValid       whether structured output validation succeeded
     * @param toolCalls         number of tool calls performed during execution
     */
    record LlmNodeMetrics(
            Duration duration,
            Long promptTokens,
            Long completionTokens,
            long costMicros,
            ModelInfo modelInfo,
            Boolean outputValid,
            Integer toolCalls
    ) implements NodeMetrics {

        /**
         * Total tokens (prompt + completion), or {@code null} if neither is
         * present.
         */
        public Long totalTokens() {
            if (promptTokens == null && completionTokens == null) {
                return null;
            }

            return (promptTokens == null ? 0L : promptTokens)
                    + (completionTokens == null ? 0L : completionTokens);
        }

        /**
         * Cost expressed in US dollars.
         */
        public BigDecimal costUsd() {
            return BigDecimal.valueOf(costMicros, 6);
        }
    }
}