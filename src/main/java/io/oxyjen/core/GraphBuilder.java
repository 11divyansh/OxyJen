package io.oxyjen.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

import io.oxyjen.graph.edges.ConditionalEdge;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.edges.DirectEdge;

/**
 * Builder pattern for constructing {@link Graph} instances.
 * Simplifies adding nodes and ensures clean creation flow.
 */
public class GraphBuilder {

    private String name;
    private boolean allowCycles = false;
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
    /**
     * Connection with conditional logic
     */
    public <O> GraphBuilder connectConditional(
            String from,
            String to,
            BiPredicate<O, NodeContext> predicate
    ) {
        NodePlugin<?, ?> source = getNode(from);
        NodePlugin<?, ?> target = getNode(to);
        edges.add(new ConditionalEdge<>(source, target, predicate));
        return this;
    }
    public RouteBuilder route(
            String from,
            Function<NodeContext, String> router
    ) {
        NodePlugin<?, ?> source = getNode(from);
        return new RouteBuilder(this, source, router);
    }
    /** To use cyclic edge to loop back to same node.*/
    public LoopBuilder repeat(String nodeName) {
        this.allowCycles(); 
        NodePlugin<?, ?> source = getNode(nodeName);
        return new LoopBuilder(this, source);
    }
    /** To use cyclic edge to loop to different node (previous/some other node).*/
    public LoopBuilder loop(String from) {
        this.allowCycles(); 
        NodePlugin<?, ?> source = getNode(from);
        return new LoopBuilder(this, source);
    }
    /** Create bulk node registration to run in parallel*/
    public GraphBuilder addParallelNodes(Object... nameNodePairs) {
        if (nameNodePairs.length % 2 != 0) {
            throw new IllegalArgumentException(
                "Arguments must be in pairs: (name, node)"
            );
        }
        for (int i = 0; i < nameNodePairs.length; i += 2) {
            Object nameObj = nameNodePairs[i];
            Object nodeObj = nameNodePairs[i + 1];
            if (!(nameObj instanceof String name)) {
                throw new IllegalArgumentException("Expected String for node name");
            }
            if (!(nodeObj instanceof NodePlugin<?, ?> node)) {
                throw new IllegalArgumentException("Expected NodePlugin for node");
            }
            addNode(name, node);
        }
        return this;
    }
    /** To explicitly allow cycles, and restrain user from creating infinite loop*/
    public GraphBuilder allowCycles() {
        this.allowCycles = true;
        return this;
    }

    public Graph build() {
        Graph graph = new Graph(name, allowCycles);
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
    public static class RouteBuilder {
        private final GraphBuilder builder;
        private final NodePlugin<?, ?> source;
        private final Function<NodeContext, String> router;
        RouteBuilder(GraphBuilder builder,
                     NodePlugin<?, ?> source,
                     Function<NodeContext, String> router) {
            this.builder = builder;
            this.source = source;
            this.router = router;
        }
        public RouteBuilder to(String key, String targetName) {
            NodePlugin<?, ?> target = builder.getNode(targetName);

            builder.edges.add(new ConditionalEdge<>(
                source,
                target,
                (out, ctx) -> key.equals(router.apply(ctx))
            ));
            return this;
        }
    }
    public class LoopBuilder {

        private final GraphBuilder builder;
        private final NodePlugin<?, ?> source;
        private NodePlugin<?, ?> target;
        private BiPredicate<Object, NodeContext> condition;
        private int maxIterations = 3;

        LoopBuilder(GraphBuilder builder, NodePlugin<?, ?> source) {
            this.builder = builder;
            this.source = source;
            this.target = source;
        }
        /** Use when looping back to different loop*/
        public LoopBuilder to(String targetName) {
            this.target = builder.getNode(targetName);
            return this;
        }

        public LoopBuilder whileCondition(BiPredicate<Object, NodeContext> condition) {
            this.condition = condition;
            return this;
        }

        public LoopBuilder max(int max) {
            this.maxIterations = max;
            return this;
        }

        public GraphBuilder build() {
        	if (condition == null) {
        		throw new IllegalStateException("Loop condition must be defined");
        	}
        	if (maxIterations < 1) {
        		throw new IllegalArgumentException("maxIterations must be >= 1");
        	}
            builder.edges.add(new CyclicEdge(
                source,
                target,
                condition,
                maxIterations
            ));
            return builder;
        }
    }
}