package io.oxyjen.graph.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.edges.CyclicEdge;

/**
 * Validates the structural integrity of a {@link Graph} before execution.
 *
 * Checks performed:
 * 1. Basic graph invariants (delegated to {@link Graph#validate()}).
 * 2. Cycle detection - only {@link CyclicEdge}s are permitted to form cycles.
 *    Any cycle formed by {@link DirectEdge} or {@link ConditionalEdge} is illegal.
 * 3. Orphan detection - nodes with no edges (not root, not terminal) are warned.
 * 4. Unreachable node detection - nodes that can never be reached from any root.
 *
 * Usage:
 * <pre>{@code
 *   DAGValidator.validate(graph);           // throws on invalid graph
 *   DAGValidator.ValidationResult r = DAGValidator.inspect(graph); // returns result for logging
 * }</pre>
 */
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
        detectIllegalCycles(graph, errors);
        detectUnreachableNodes(graph, warnings);
        detectOrphanNodes(graph, warnings);
        return new ValidationResult(errors, warnings);
    }
  
    // Cycle detection (DFS on non-cyclic edges only)
    private static void detectIllegalCycles(Graph graph, List<String> errors) {
        // Build adjacency using only DirectEdge and ConditionalEdge (CyclicEdge excluded)
        Map<NodePlugin<?, ?>, List<NodePlugin<?, ?>>> strictAdj = new LinkedHashMap<>();
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            strictAdj.put(node, new ArrayList<>());
        }
        for (Edge edge : graph.getAllEdges()) {
            if (!(edge instanceof CyclicEdge)) {
                strictAdj.get(edge.getSource()).add(edge.getTarget());
            }
        }
        // DFS cycle detection
        Set<NodePlugin<?, ?>> visited = new HashSet<>();
        Set<NodePlugin<?, ?>> inStack = new HashSet<>();
        Set<NodePlugin<?, ?>> roots = graph.getRootNodes();
        for (NodePlugin<?, ?> root : roots) {
            if (!visited.contains(root)) {
                Deque<NodePlugin<?, ?>> cycle = detectCycleDFS(root, strictAdj, visited, inStack, new ArrayDeque<>());
                if (cycle != null) {
                    errors.add("Illegal cycle detected (use CyclicEdge for intentional loops): "
                        + formatCycle(cycle));
                }
            }
        }
    }
 
    private static Deque<NodePlugin<?, ?>> detectCycleDFS(
            NodePlugin<?, ?> node,
            Map<NodePlugin<?, ?>, List<NodePlugin<?, ?>>> adj,
            Set<NodePlugin<?, ?>> visited,
            Set<NodePlugin<?, ?>> inStack,
            Deque<NodePlugin<?, ?>> path
    ) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);
 
        for (NodePlugin<?, ?> neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (!visited.contains(neighbor)) {
                Deque<NodePlugin<?, ?>> result = detectCycleDFS(neighbor, adj, visited, inStack, path);
                if (result != null) return result;
            } else if (inStack.contains(neighbor)) {
                // found cycle - capture the cycle portion of the path
                Deque<NodePlugin<?, ?>> cycle = new ArrayDeque<>();
                boolean inCycle = false;
                for (NodePlugin<?, ?> n : path) {
                    if (n == neighbor) inCycle = true;
                    if (inCycle) cycle.addLast(n);
                }
                cycle.addLast(neighbor);
                return cycle;
            }
        }
 
        inStack.remove(node);
        path.removeLast();
        return null;
    }
    private static String formatCycle(Deque<NodePlugin<?, ?>> cycle) {
        StringBuilder sb = new StringBuilder();
        for (NodePlugin<?, ?> n : cycle) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(n.getName());
        }
        return sb.toString();
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