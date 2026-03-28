package io.oxyjen.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
  * A directed acyclic graph (DAG) of {@link NodePlugin} nodes connected by {@link Edge}s.
 *
 * Breaking change from v0.4: Graph is no longer a sequential list.
 * It now maintains an adjacency map where each node maps to its outgoing edges.
 *
 * Key concepts:
 * - A node with no incoming edges is a "root" (entry point).
 * - A node with no outgoing edges is a "terminal" (exit point).
 * - Multiple roots = fan-in start. Multiple edges from one node = fan-out / branching.
 * 
 * Usage:
 * <pre>{@code
 *   Graph graph = new Graph("my-pipeline");
 *   graph.addNode(fetchNode);
 *   graph.addNode(enrichNode);
 *   graph.addNode(summaryNode);
 *   graph.addNode(translateNode);
 *
 *   graph.addEdge(fetchNode, enrichNode); // direct
 *   }</pre>
 */
public class Graph {

    private final String name;
    private final LinkedHashSet<NodePlugin<?, ?>> nodes = new LinkedHashSet<>();
    private final Map<NodePlugin<?, ?>, List<Edge>> adjacency = new LinkedHashMap<>();
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
        adjacency.putIfAbsent(node, new ArrayList<>());
        return this;
    }
    
    /**
     * Adds a {@link DirectEdge} between two nodes (shorthand for the common case).
     *
     * Both nodes are auto-registered if not already present.
     */
    public Graph addEdge(NodePlugin<?, ?> source, NodePlugin<?, ?> target) {
        return addEdge(new DirectEdge(source, target));
    }
    
    /**
     * Adds any {@link Edge} subtype (DirectEdge).
     *
     * Both source and target are auto-registered if not already present.
     */
    public Graph addEdge(Edge edge) {
        Objects.requireNonNull(edge, "Edge must not be null");
        addNode(edge.getSource());
        addNode(edge.getTarget());
        adjacency.get(edge.getSource()).add(edge);
        return this;
    }

    /**
     * Returns an unmodifiable view of nodes in insertion order.
     */
    public Set<NodePlugin<?, ?>> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }
    
    /**
     * Returns outgoing edges from a given node (empty list if none).
     */
    public List<Edge> getEdgesFrom(NodePlugin<?, ?> node) {
        return Collections.unmodifiableList(
            adjacency.getOrDefault(node, Collections.emptyList())
        );
    }
    
    /**
     * Returns all edges in the graph (flattened from all adjacency lists).
     */
    public List<Edge> getAllEdges() {
        List<Edge> all = new ArrayList<>();
        adjacency.values().forEach(all::addAll);
        return Collections.unmodifiableList(all);
    }
    
    /**
     * Returns nodes with no outgoing edges (graph terminal/exit points).
     */
    public Set<NodePlugin<?, ?>> getTerminalNodes() {
        Set<NodePlugin<?, ?>> terminals = new LinkedHashSet<>();
        for (NodePlugin<?, ?> node : nodes) {
            if (adjacency.getOrDefault(node, Collections.emptyList()).isEmpty()) {
                terminals.add(node);
            }
        }
        return Collections.unmodifiableSet(terminals);
    }
 
    /**
     * @return Whether the node is registered in this graph.
     */
    public boolean containsNode(NodePlugin<?, ?> node) {
        return nodes.contains(node);
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