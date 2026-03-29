package io.oxyjen.core;

import java.util.function.BiPredicate;

/**
 * A conditional directed edge that is only traversed when a predicate returns {@code true}.
 *
 * This is the primary mechanism for branching in OxyJen DAGs. Multiple
 * ConditionalEdges can originate from the same source node - the executor
 * evaluates all outgoing edges and follows every one whose predicate passes.
 *
 * If no outgoing edge passes its predicate, execution terminates at that node
 * (treated as a terminal node for that execution path).
 *
 * Usage:
 * <pre>{@code
 *   // Branch on context value
 *   graph.addEdge(new ConditionalEdge(routerNode, summaryNode,
 *       (output, ctx) -> "summary".equals(ctx.get("mode"))));
 *
 *   // Branch on node output type
 *   graph.addEdge(new ConditionalEdge(classifyNode, refuseNode,
 *       (output, ctx) -> output instanceof String s && s.contains("UNSAFE")));
 * }</pre>
 *
 * @param <O> The output type of the source node (used for type-safe predicate authoring).
 */
public class ConditionalEdge<O> extends Edge{

	private final BiPredicate<O, NodeContext> predicate;
	/**
     * @param source    The source node.
     * @param target    The target node (executed only when predicate returns true).
     * @param predicate Receives the source node's output and the shared context.
     *                  Return {@code true} to traverse this edge.
     */
	protected ConditionalEdge(NodePlugin<?, ?> source, 
			NodePlugin<?, ?> target, 
			BiPredicate<O, NodeContext> predicate) {
		super(source, target);
		if (predicate == null) throw new IllegalArgumentException("Predicate must not be null");
	    this.predicate = predicate;
	}
	@Override
    @SuppressWarnings("unchecked")
    public boolean shouldTraverse(Object output, NodeContext context) {
        try {
            return predicate.test((O) output, context);
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                "ConditionalEdge [" + getLabel() + "] predicate received unexpected output type: "
                    + (output == null ? "null" : output.getClass().getName()), e
            );
        }
    }
 
    @Override
    public String getLabel() {
        return super.getLabel() + " (conditional)";
    }
}