package io.oxyjen.graph.validation;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.branching.BranchNode;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;
import io.oxyjen.graph.edges.ConditionalEdge;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.edges.DirectEdge;
import io.oxyjen.graph.edges.FailureEdge;

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
        detectTypeIncompatibilities(graph, errors, warnings);
        return new ValidationResult(errors, warnings);
    }
  
    // Cycle detection (DFS on non-cyclic edges only)
    private static void detectIllegalCycles(Graph graph, List<String> errors) {
        // Build adjacency using only normal success-path edges.
        Map<NodePlugin<?, ?>, List<NodePlugin<?, ?>>> strictAdj = new LinkedHashMap<>();
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            strictAdj.put(node, new ArrayList<>());
        }
        for (Edge edge : graph.getAllEdges()) {
            if (!(edge instanceof CyclicEdge) && !(edge instanceof FailureEdge)) {
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
    
    // acts as a compile-time safety net until auto unwrapping, output adapters or typed builder is introduced
    private static void detectTypeIncompatibilities(
            Graph graph,
            List<String> errors,
            List<String> warnings
    ) {
        for (Edge edge : graph.getAllEdges()) {
            // skip failure/cyclic - they carry NodeFailure or loop back, not typed
            if (edge instanceof FailureEdge || edge instanceof CyclicEdge) continue;

            NodePlugin<?, ?> source = edge.getSource().unwrap();
            NodePlugin<?, ?> target = edge.getTarget().unwrap();
            
            // These nodes have special output handling in ParallelExecutor
            if (source instanceof BranchNode ||
            	source instanceof RouterNode ||
            	source instanceof MergeNode) continue;
            Type sourceOutput = resolveOutputType(source);
            Type targetInput  = resolveInputType(target);

            if (sourceOutput == null || targetInput == null) continue; // can't determine
            

            // Object input accepts anything. Common in prompt nodes
            if (isObjectType(targetInput) || isObjectType(sourceOutput)) continue;
            if (!isAssignable(sourceOutput, targetInput)) {
                errors.add("Type mismatch on edge [" + edge.getSource().getName()
                    + "] -> [" + edge.getTarget().getName() + "]: "
                    + "source outputs [" + typeName(sourceOutput) + "] "
                    + "but target expects [" + typeName(targetInput) + "]. "
                    + "Check your .connect() call in GraphBuilder."
                );
            }
        }
    }
    
    /**
     * Resolves the OUTPUT type (O) of a NodePlugin<I, O> implementation.
     * Walks generic interfaces and superclass to find NodePlugin parameterization.
     */
    private static Type resolveOutputType(NodePlugin<?, ?> node) {
        return resolveTypeArg(node.getClass(), 1);
    }

    /**
     * Resolves the INPUT type (I) of a NodePlugin<I, O> implementation.
     */
    private static Type resolveInputType(NodePlugin<?, ?> node) {
        return resolveTypeArg(node.getClass(), 0);
    }
    
    private static Type resolveTypeArg(Class<?> clazz, int argIndex) {
        if (clazz == null || clazz == Object.class) return null;

        // Check direct interfaces
        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt
                    && pt.getRawType() instanceof Class<?> raw
                    && raw == NodePlugin.class) {
                Type arg = pt.getActualTypeArguments()[argIndex];
                // If it's a TypeVariable (e.g. <T>) we can't resolve - return null
                if (arg instanceof TypeVariable<?>) return null;
                return arg;
            }
        }

        // Check superclass (for abstract base classes implementing NodePlugin)
        Type superclass = clazz.getGenericSuperclass();
        if (superclass instanceof ParameterizedType pt
                && pt.getRawType() instanceof Class<?> raw
                && raw == NodePlugin.class) {
            Type arg = pt.getActualTypeArguments()[argIndex];
            if (arg instanceof TypeVariable<?>) return null;
            return arg;
        }

        // Recurse up the hierarchy
        if (superclass instanceof Class<?> sc) {
            Type result = resolveTypeArg(sc, argIndex);
            if (result != null) return result;
        }

        // Recurse into interfaces
        for (Type iface : clazz.getGenericInterfaces()) {
            if (iface instanceof Class<?> ic) {
                Type result = resolveTypeArg(ic, argIndex);
                if (result != null) return result;
            }
        }
        return null;
    }
    
    /**
     * Checks if sourceOutput is assignable to targetInput.
     * Handles Class, ParameterizedType, wildcards.
     */
    private static boolean isAssignable(Type sourceOutput, Type targetInput) {
    	Class<?> sourceRaw = rawType(sourceOutput);
    	Class<?> targetRaw = rawType(targetInput);
    	if (sourceRaw == null || targetRaw == null) return false;
    	return targetRaw.isAssignableFrom(sourceRaw);
    }
    
    /** Extracts the raw Class from any Type.*/
    private static Class<?> rawType(Type type) {
    	if (type instanceof Class<?> c) return c;
    	if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) return c;
    	if (type instanceof WildcardType wt) {
    		Type[] upper = wt.getUpperBounds();
    		if (upper.length > 0) return rawType(upper[0]);
    	}
    	if (type instanceof GenericArrayType gat) {
    		Class<?> component = rawType(gat.getGenericComponentType());
    		if (component != null) return Array.newInstance(component, 0).getClass();
    	}
    	return null;
    }
    
    private static String typeName(Type type) {
        if (type instanceof Class<?> c) return c.getSimpleName();
        if (type instanceof ParameterizedType pt) {
            String raw = rawType(pt) != null ? rawType(pt).getSimpleName() : "?";
            String args = Arrays.stream(pt.getActualTypeArguments())
                    .map(DAGValidator::typeName)
                    .collect(Collectors.joining(", "));
            return raw + "<" + args + ">";
        }
        return type.getTypeName();
    }
    
    private static boolean isObjectType(Type type) {
        return type instanceof Class<?> c && c == Object.class;
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
            errors.forEach(e -> sb.append("  ERROR: ").append(e).append("\n"));
            warnings.forEach(w -> sb.append("  WARNING: ").append(w).append("\n"));
            return sb.toString().trim();
        }
 
        @Override
        public String toString() {
            return "ValidationResult[errors=" + errors.size() + ", warnings=" + warnings.size() + "]";
        }
    }
}