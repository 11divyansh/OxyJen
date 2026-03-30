package io.oxyjen.graph.edges;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodePlugin;

/**
 * A simple unconditional directed edge.
 *
 * The target node is always executed after the source node completes.
 * This is the default edge type used by {@link Graph#addEdge(NodePlugin, NodePlugin)}.
 *
 * Usage:
 * <pre>{@code
 *   graph.addEdge(nodeA, nodeB);         // shorthand creates a DirectEdge internally
 *   graph.addEdge(new DirectEdge(a, b)); // explicit form
 * }</pre>
 */
public final class DirectEdge extends Edge {
 
    public DirectEdge(NodePlugin<?, ?> source, NodePlugin<?, ?> target) {
        super(source, target);
    }
}