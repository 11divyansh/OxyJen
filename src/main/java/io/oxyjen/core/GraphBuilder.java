package io.oxyjen.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builder pattern for constructing {@link Graph} instances.
 * Simplifies adding nodes and ensures clean creation flow.
 */
public class GraphBuilder {

    private String name = "unnamed-graph";
    Map<String, NodePlugin<?, ?>> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    public static GraphBuilder named(String name) {
        GraphBuilder builder = new GraphBuilder();
        builder.name = Objects.requireNonNull(name);
        return builder;
    }

    // Node registration with name
    public GraphBuilder addNode(String name, NodePlugin<?, ?> node) {
        Objects.requireNonNull(name, "Node name cannot be null");
        Objects.requireNonNull(node, "Node cannot be null");
        if (nodes.containsKey(name)) {
            throw new IllegalArgumentException(
                "Duplicate node name: " + name
            );
        }
        nodes.put(name, node);
        return this;
    }
    /**
     * Simple direct connection (A -> B)
     */
    public GraphBuilder connect(String from, String to) {
        NodePlugin<?, ?> source = getNode(from);
        NodePlugin<?, ?> target = getNode(to);
        edges.add(new DirectEdge(source, target));
        return this;
    }

    public Graph build() {
        Graph graph = new Graph(name);
        for (NodePlugin<?, ?> node : nodes.values()) {
            graph.addNode(node);
        }
        for (Edge edge : edges) {
        	graph.addEdge(edge);
        }
        graph.validate();
        return graph;
    }
    private NodePlugin<?, ?> getNode(String name){
    	NodePlugin<?, ?> node = nodes.get(name);
    	if (node == null) {
    		throw new IllegalArgumentException("Node not found: " + name);
    	}
    	return node;
    }
}