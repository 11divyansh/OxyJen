package io.oxyjen.execution;

import java.util.Objects;

import io.oxyjen.core.NodeContext;

/**
 * Shared metadata keys and execution-scoped helpers.
 *
 * <p>Nodes can publish rich node metrics in the context and the executor can
 * read them back after {@code process()} returns. Lifecycle event ownership
 * still stays with the executor.
 */
public final class ExecutionMetadataKeys {

    private static final ThreadLocal<String> CURRENT_NODE_ID = new ThreadLocal<>();

    public static final String NODE_METRICS_PREFIX = "execution.nodeMetrics:";

    private ExecutionMetadataKeys() {}

    public static void setCurrentNodeId(String nodeId) {
        CURRENT_NODE_ID.set(nodeId);
    }

    public static void clearCurrentNodeId() {
        CURRENT_NODE_ID.remove();
    }

    public static String currentNodeId(String fallback) {
        String nodeId = CURRENT_NODE_ID.get();
        return nodeId != null ? nodeId : Objects.requireNonNull(fallback, "fallback must not be null");
    }

    public static String nodeMetricsKey(String nodeId) {
        return NODE_METRICS_PREFIX + Objects.requireNonNull(nodeId, "nodeId must not be null");
    }

    public static String nodeMetricsKey(NodeContext context, String fallbackNodeId) {
        return nodeMetricsKey(currentNodeId(fallbackNodeId));
    }
}
