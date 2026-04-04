package io.oxyjen.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.validation.DAGValidator;

public class ParallelExecutor {

	private final ForkJoinPool pool;
	 
    /** Default: uses the common pool.*/
    public ParallelExecutor() {
        this.pool = ForkJoinPool.commonPool();
    }
 
    /** Custom thread pool for controlled parallelism. */
    public ParallelExecutor(ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
    }
 
    /**
     * Runs the graph and returns outputs from all terminal nodes, keyed by node name.
     *
     * @param graph   The DAG to execute.
     * @param input   The input passed to all root nodes.
     * @param context The shared execution context.
     * @return Map of terminalNode.getName() -> output.
     */
    public Map<String, Object> run(Graph graph, Object input, NodeContext context) {
        DAGValidator.validate(graph);
        context.setMetadata("graphName", graph.getName());
 
        // nodeOutput[node] = the output it produced (filled as nodes complete)
        Map<NodePlugin<?, ?>, Object> nodeOutputs = new ConcurrentHashMap<>();
 
        // fan-in tracking: count how many upstream nodes still need to complete
        // before a given node can start. Keyed by target node.
        Map<NodePlugin<?, ?>, Integer> pendingIncoming = computePendingIncoming(graph);
        Set<NodePlugin<?, ?>> cyclicTargets = findCyclicTargets(graph);
        // Completed set - guards against re-executing non-cyclic nodes
        Set<NodePlugin<?, ?>> completed = ConcurrentHashMap.newKeySet();
        Set<NodePlugin<?, ?>> inProgress = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> rootFutures = new ArrayList<>();
        for (NodePlugin<?, ?> root : graph.getRootNodes()) {
        	if (inProgress.add(root))
        		rootFutures.add(
        				executeNodeAsync(root, input, graph, context, nodeOutputs, pendingIncoming, completed, inProgress, cyclicTargets)
        		);
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            rootFutures.toArray(new CompletableFuture[0])
        );
        try {
            allDone.get(); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Graph execution interrupted: " + graph.getName(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException("Graph execution failed: " + graph.getName(), cause);
        }
        Map<String, Object> results = new LinkedHashMap<>();
        for (NodePlugin<?, ?> terminal : graph.getTerminalNodes()) {
            results.put(terminal.getName(), nodeOutputs.get(terminal));
        }
        return results;
    }
    
    @SuppressWarnings("unchecked")
    private CompletableFuture<Void> executeNodeAsync(
            NodePlugin<?, ?> node,
            Object input,
            Graph graph,
            NodeContext context,
            Map<NodePlugin<?, ?>, Object> nodeOutputs,
            Map<NodePlugin<?, ?>, Integer> pendingIncoming,
            Set<NodePlugin<?, ?>> completed,
            Set<NodePlugin<?, ?>> inProgress,
            Set<NodePlugin<?, ?>> cyclicTargets
    ) {
        return CompletableFuture.supplyAsync(() -> {
            context.getLogger().info("[DAG] Executing: " + node.getName());
            NodePlugin<Object, Object> safeNode = (NodePlugin<Object, Object>) node;
            safeNode.onStart(context);
            Object output;
            try {
                output = safeNode.process(input, context);
                safeNode.onFinish(context);
                context.getLogger().info("[DAG] Completed: " + node.getName());
            } catch (Exception e) {
                context.getLogger().severe("[DAG] Error in node [" + node.getName() + "]: " + e.getMessage());
                try { context.getExceptionHandler().handleException(safeNode, e, context); } catch (Exception ignored) {}
                try { safeNode.onError(e, context); } catch (Exception ignored) {}
                throw new RuntimeException("Node failed: " + node.getName(), e);
            } 
            nodeOutputs.put(node, output);
            completed.add(node);
            inProgress.remove(node);
            return output;
        }, pool).thenCompose(output -> {
            // Fan-out: evaluate all outgoing edges and schedule eligible targets
            List<CompletableFuture<Void>> downstream = new ArrayList<>(); 
            for (Edge edge : graph.getEdgesFrom(node)) {
                if (!edge.shouldTraverse(output, context)) {
                    context.getLogger().info("[DAG] Skipping edge: " + edge.getLabel());
                    continue;
                } 
                NodePlugin<?, ?> target = edge.getTarget();
                boolean isCyclic = edge instanceof CyclicEdge;
                if(!isCyclic && !cyclicTargets.contains(target)) {
                	// Fan-in: decrement pending counter; only proceed when all incoming are done
                	int remaining = pendingIncoming.merge(target, -1, Integer::sum);
                	if (remaining != 0) {
                		context.getLogger().info(
                				"[DAG] Node [" + target.getName() + "] waiting for " + remaining + " more upstream nodes."
                		);
                		continue;
                	}
                	if (!inProgress.add(target)) continue;
                } else {
                	// For CyclicEdge, always allow re-execution but still guard duplicate scheduling
                	if(!inProgress.add(target)) continue;
                }
                // TODO v0.5: MergeNode will aggregate multiple inputs properly.
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, pendingIncoming, completed, inProgress, cyclicTargets)
                );
            }
            if (downstream.isEmpty()) return CompletableFuture.completedFuture(null);
            return CompletableFuture.allOf(downstream.toArray(new CompletableFuture[0]));
        });
    }
    /**
     * Computes how many non-cyclic incoming edges each node has.
     * Used for fan-in synchronization - a node only runs when all its upstream nodes are done.
     */
    private Map<NodePlugin<?, ?>, Integer> computePendingIncoming(Graph graph) {
        Map<NodePlugin<?, ?>, Integer> incoming = new ConcurrentHashMap<>();
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            incoming.put(node, 0);
        }
        for (Edge edge : graph.getAllEdges()) {
            if (!(edge instanceof CyclicEdge)) {
                incoming.merge(edge.getTarget(), 1, Integer::sum);
            }
        }
        return incoming;
    }
    
    private Set<NodePlugin<?, ?>> findCyclicTargets(Graph graph) {
        Set<NodePlugin<?, ?>> set = ConcurrentHashMap.newKeySet();
        for (Edge e : graph.getAllEdges()) {
            if (e instanceof CyclicEdge) {
                set.add(e.getTarget());
            }
        }
        return set;
    }
}