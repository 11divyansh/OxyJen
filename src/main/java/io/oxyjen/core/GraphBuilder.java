package io.oxyjen.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builder pattern for constructing {@link Graph} instances.
 * Simplifies adding nodes and ensures clean creation flow.
 */
public class GraphBuilder {

    private String name = "unnamed-graph";
    private final List<NodePlugin<?, ?>> nodes = new ArrayList<>();

    public static GraphBuilder named(String name) {
        GraphBuilder builder = new GraphBuilder();
        builder.name = Objects.requireNonNull(name);
        return builder;
    }

    public GraphBuilder addNode(NodePlugin<?, ?> node) {
        Objects.requireNonNull(node, "Node cannot be null");
        nodes.add(node);
        return this;
    }

    public Graph build() {
        Graph graph = new Graph(name);
        for (NodePlugin<?, ?> node : nodes) {
            graph.addNode(node);
        }
        return graph;
    }
}
