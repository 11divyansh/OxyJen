package io.oxyjen.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Minimal sequential Graph: stores nodes in a list and exposes them in insertion order.
 * when you only need simple pipelines.
 */
public class Graph {

    private final String name;
    private final List<NodePlugin<?, ?>> nodes = new ArrayList<>();

    public Graph() {
        this("unnamed-graph");
    }

    public Graph(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Add a node to the end of the pipeline.
     */
    public Graph addNode(NodePlugin<?, ?> node) {
        Objects.requireNonNull(node, "node must not be null");
        nodes.add(node);
        return this;
    }

    /**
     * Returns an unmodifiable view of nodes in insertion order.
     */
    public List<NodePlugin<?, ?>> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public String getName() {
        return name;
    }

    /**
     * Simple validation: ensure graph has at least one node.
     */
    public void validate() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Graph [" + name + "] contains no nodes");
        }
    }
}
