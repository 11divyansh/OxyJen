package io.oxyjen.graph.edges;

import java.util.function.BiPredicate;

import io.oxyjen.core.Edge;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * A cyclic edge that routes execution back to a previous node (creating a loop).
 *
 * CyclicEdges are the escape hatch from DAG purity - they allow retry loops,
 * agentic "think again" cycles, and refinement pipelines while still providing
 * deterministic termination via a hard {@code maxIterations} cap.
 *
 * The executor tracks how many times this specific edge has been traversed
 * (keyed by edge identity stored in context metadata) and refuses further
 * traversal once the cap is exceeded, even if the predicate still returns true.
 *
 * Usage:
 * <pre>{@code
 *   // Retry: loop back to LLM node if output fails validation, max 3 times
 *   graph.addEdge(new CyclicEdge<>(validateNode, llmNode,
 *       (output, ctx) -> !output.isValid(),
 *       3));
 *
 *   // Refinement loop: keep refining until quality passes or limit hit
 *   graph.addEdge(new CyclicEdge<>(qualityNode, refineNode,
 *       (score, ctx) -> score < 0.85,
 *       5));
 * }</pre>
 */
public final class CyclicEdge extends Edge {
 
    /** Context metadata key prefix for tracking iteration counts. */
    static final String ITERATION_KEY_PREFIX = "__cyclic_edge_iter__";
    private final BiPredicate<Object, NodeContext> predicate;
    private final int maxIterations;
 
    /**
     * @param source        The source node (evaluated after it runs).
     * @param target        The target node to loop back to.
     * @param predicate     Return {@code true} to continue the loop.
     * @param maxIterations Hard cap on how many times this loop can run.
     *                      When reached, traversal stops regardless of predicate.
     */
    public CyclicEdge(
            NodePlugin<?, ?> source,
            NodePlugin<?, ?> target,
            BiPredicate<Object, NodeContext> predicate,
            int maxIterations
    ) {
        super(source, target);
        if (predicate == null) throw new IllegalArgumentException("Predicate must not be null");
        if (maxIterations < 1) throw new IllegalArgumentException("maxIterations must be >= 1");
        this.predicate = predicate;
        this.maxIterations = maxIterations;
    }
 
    /** Constructor with default cap of 3 iterations. */
    public CyclicEdge(
            NodePlugin<?, ?> source,
            NodePlugin<?, ?> target,
            BiPredicate<Object, NodeContext> predicate
    ) {
        this(source, target, predicate, 3);
    }
 
    @Override
    @SuppressWarnings("unchecked")
    public boolean shouldTraverse(Object output, NodeContext context) {
        String key = iterationKey();
        Integer currentVal = context.getMetadata(key);
        int current = currentVal != null ? currentVal : 0;
        if (current >= maxIterations) {
            context.getLogger().warning(
                "CyclicEdge [" + getLabel() + "] reached max iterations (" + maxIterations + "). Stopping loop."
            );
            return false;
        } 
        boolean shouldLoop;
        try {
            shouldLoop = predicate.test(output, context);
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                "CyclicEdge [" + getLabel() + "] predicate received unexpected output type: "
                    + (output == null ? "null" : output.getClass().getName()), e
            );
        }
        if (shouldLoop) {
            context.setMetadata(key, current + 1);
        }
        return shouldLoop;
    }
 
    /**
     * Unique key to track this edge's iteration count in the context metadata.
     * Uses system identity hash to differentiate between multiple CyclicEdge instances.
     */
    String iterationKey() {
        return ITERATION_KEY_PREFIX + System.identityHashCode(this);
    }
 
    public int getMaxIterations() {
        return maxIterations;
    }
 
    @Override
    public String getLabel() {
        return super.getLabel() + " (cyclic, max=" + maxIterations + ")";
    }
}