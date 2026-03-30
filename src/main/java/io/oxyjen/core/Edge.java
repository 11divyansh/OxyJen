package io.oxyjen.core;

/**
 * Represents a directed edge between two nodes in the DAG.
 *
 * An edge connects a source node to a target node and can optionally carry
 * a condition that determines whether traversal should happen at runtime.
 * 
 * Edge types:
 * - {@link DirectEdge}      – always traversed (default)
 * - {@link ConditionalEdge} – traversed only when predicate returns true
 * - {@link CyclicEdge}      – loops back to a previous node (subject to cycle limit)
 */
public abstract class Edge {
	 
    private final NodePlugin<?, ?> source;
    private final NodePlugin<?, ?> target;
 
    protected Edge(NodePlugin<?, ?> source, NodePlugin<?, ?> target) {
        if (source == null) throw new IllegalArgumentException("Edge source must not be null");
        if (target == null) throw new IllegalArgumentException("Edge target must not be null");
        this.source = source;
        this.target = target;
    }
 
    /**
     * @return The node this edge originates from.
     */
    public NodePlugin<?, ?> getSource() {
        return source;
    }
 
    /**
     * @return The node this edge points to.
     */
    public NodePlugin<?, ?> getTarget() {
        return target;
    }
 
    /**
     * Determines if this edge should be traversed given the current output and context.
     *
     * Default implementation always returns true (unconditional edge).
     * Override in {@link ConditionalEdge} and {@link CyclicEdge} to add routing logic.
     *
     * @param output  The output produced by the source node.
     * @param context The shared execution context.
     * @return true if traversal should proceed to the target node.
     */
    public boolean shouldTraverse(Object output, NodeContext context) {
        return true;
    }
 
    /**
     * Human-readable label for this edge (used in logs).
     */
    public String getLabel() {
        return source.getName() + " --> " + target.getName();
    }
 
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getLabel() + "]";
    }
}