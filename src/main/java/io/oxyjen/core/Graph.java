package io.oxyjen.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.edges.DirectEdge;

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
 * - {@link CyclicEdge}s are the only permitted backward edges (controlled loops).
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
 *   graph.addEdge(new ConditionalEdge<>(enrichNode,      		// branch
 *       summaryNode, (out, ctx) -> ctx.get("lang").equals("en")));
 *   graph.addEdge(new ConditionalEdge<>(enrichNode,
 *       translateNode, (out, ctx) -> !ctx.get("lang").equals("en")));
 *   }</pre>
 */
public class Graph {

    private final String name;
    private final boolean allowCycles;
    private final LinkedHashSet<NodePlugin<?, ?>> nodes = new LinkedHashSet<>();
    private final Map<NodePlugin<?, ?>, List<Edge>> adjacency = new LinkedHashMap<>();

    Graph(String name, boolean allowCycles) {
    	this.name = (name == null || name.isBlank())
                ? "graph-" + UUID.randomUUID()
                : name;
    	this.allowCycles = allowCycles;
    }
    /**
     * Create unnamed graph for quick testing
     * Usage : Graph.builder().addNode(...).build();
     * @return
     */
    public static GraphBuilder builder() {
        return new GraphBuilder();
    }
    /**
     * Usage : Graph.builder(name).addNode(...).build();
     */
    public static GraphBuilder builder(String name) {
        return GraphBuilder.named(name);
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
     * @return Nodes with no incoming edges (graph entry points).
     */
    public Set<NodePlugin<?, ?>> getRootNodes() {
        Set<NodePlugin<?, ?>> hasIncoming = new HashSet<>();
        for (List<Edge> edges : adjacency.values()) {
            for (Edge e : edges) {
                // CyclicEdges don't count as real incoming for root detection
                if (!(e instanceof CyclicEdge)) {
                    hasIncoming.add(e.getTarget());
                }
            }
        }
        Set<NodePlugin<?, ?>> roots = new LinkedHashSet<>(nodes);
        roots.removeAll(hasIncoming);
        return Collections.unmodifiableSet(roots);
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
        if (getRootNodes().isEmpty()) {
            throw new IllegalStateException(
                "Graph [" + name + "] has no root nodes - every node has an incoming edge. " +
                "If you intended a loop, use CyclicEdge and ensure at least one entry point."
            );
        }
        if (getTerminalNodes().isEmpty()) {
            throw new IllegalStateException("Graph must have at least one terminal node.");
        }
        for (Edge edge : getAllEdges()) {
            if (!nodes.contains(edge.getSource())) {
                throw new IllegalStateException(
                    "Edge source [" + edge.getSource().getName() + "] is not registered in graph [" + name + "]"
                );
            }
            if (!nodes.contains(edge.getTarget())) {
                throw new IllegalStateException(
                    "Edge target [" + edge.getTarget().getName() + "] is not registered in graph [" + name + "]"
                );
            }
            if (!allowCycles && edge instanceof CyclicEdge) {
                throw new IllegalStateException(
                    "Graph [" + name + "] contains cyclic edges but cycles are not enabled. " +
                    "Call .allowCycles() on GraphBuilder."
                );
            }
        }
    }
    @Override
    public String toString() {
        return "Graph[" + name + ", nodes=" + nodes.size() + ", edges=" + getAllEdges().size() + "]";
    }
}