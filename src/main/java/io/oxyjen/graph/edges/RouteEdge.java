package io.oxyjen.graph.edges;

import io.oxyjen.core.Edge;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * Represents a connection from a {@link RouterNode} to one of its route targets.
 *
 * Unlike {@link DirectEdge} which always traverses on success, a RouteEdge
 * is CONDITIONAL - it only fires when the RouterNode's predicate for this
 * target evaluates to true and the route appears in the route map.
 *
 * RouteEdge is created automatically by {@link GraphBuilder#connect(String, String)}
 * when the source node is a RouterNode. Framework users never create RouteEdge
 * directly, it is an internal executor detail.
 *
 */
public class RouteEdge extends Edge{

	public RouteEdge(NodePlugin<?, ?> source, NodePlugin<?, ?> target) { super(source, target); }

    @Override
    public boolean shouldTraverse(Object output, NodeContext context) { return true; } // traversal decision is made by RouterNode, not the edge 

    @Override
    public boolean shouldTraverseFailure(Throwable failure, NodeContext context) { return false; } // route edges never traverse on failure

    @Override
    public String getLabel() { return "route"; }
}
