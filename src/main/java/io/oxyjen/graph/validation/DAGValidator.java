package io.oxyjen.graph.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodePlugin;

public final class DAGValidator {

	private DAGValidator() {}
	
	/**
     * Validates the graph and throws {@link IllegalStateException} if invalid.
     */
    public static void validate(Graph graph) {
        ValidationResult result = inspect(graph);
        if (!result.isValid()) {
            throw new IllegalStateException(result.summaryMessage());
        }
    }
 
    /**
     * Inspects the graph and returns a full {@link ValidationResult} without throwing.
     * Useful for logging or tooling.
     */
    public static ValidationResult inspect(Graph graph) {
        graph.validate(); 
 
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        detectUnreachableNodes(graph, warnings);
        detectOrphanNodes(graph, warnings);
        return new ValidationResult(errors, warnings);
    }
    // Unreachable node detection (BFS from all roots)
    private static void detectUnreachableNodes(Graph graph, List<String> warnings) {
        Set<NodePlugin<?, ?>> reachable = new HashSet<>();
        Queue<NodePlugin<?, ?>> queue = new LinkedList<>(graph.getRootNodes());
        while (!queue.isEmpty()) {
            NodePlugin<?, ?> current = queue.poll();
            if (reachable.add(current)) {
                for (Edge edge : graph.getEdgesFrom(current)) {
                    if (!reachable.contains(edge.getTarget())) {
                        queue.add(edge.getTarget());
                    }
                }
            }
        }
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            if (!reachable.contains(node)) {
                warnings.add("Node [" + node.getName() + "] is unreachable from any root node.");
            }
        }
    }

    // Orphan detection (no incoming and no outgoing, but not the only node
    private static void detectOrphanNodes(Graph graph, List<String> warnings) {
        if (graph.getNodes().size() <= 1) return;
        Set<NodePlugin<?, ?>> hasIncoming = new HashSet<>();
        for (Edge edge : graph.getAllEdges()) {
            hasIncoming.add(edge.getTarget());
        }
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            boolean noIncoming = !hasIncoming.contains(node);
            boolean noOutgoing = graph.getEdgesFrom(node).isEmpty();
            if (noIncoming && noOutgoing) {
                warnings.add("Node [" + node.getName() + "] is an orphan - no incoming or outgoing edges.");
            }
        }
    }
    
    public static final class ValidationResult {   	 
        private final List<String> errors;
        private final List<String> warnings;
        ValidationResult(List<String> errors, List<String> warnings) {
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
        }
 
        public boolean isValid() {
            return errors.isEmpty();
        }
 
        public List<String> getErrors() {
            return errors;
        }
 
        public List<String> getWarnings() {
            return warnings;
        }
 
        public String summaryMessage() {
            StringBuilder sb = new StringBuilder("Graph validation failed:\n");
            errors.forEach(e -> sb.append("  ERROR:   ").append(e).append("\n"));
            warnings.forEach(w -> sb.append("  WARNING: ").append(w).append("\n"));
            return sb.toString().trim();
        }
 
        @Override
        public String toString() {
            return "ValidationResult[errors=" + errors.size() + ", warnings=" + warnings.size() + "]";
        }
    }
}