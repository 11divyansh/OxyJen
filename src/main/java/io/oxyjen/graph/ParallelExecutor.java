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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.validation.DAGValidator;

public class ParallelExecutor {

	private final ForkJoinPool pool;
	private final FailureMode failureMode;
	private final Semaphore limiter;
	private final int maxConcurrency;
	 
    /** Default: uses the common pool.*/
    public ParallelExecutor() {
        this(ForkJoinPool.commonPool(), FailureMode.FAIL_FAST, Runtime.getRuntime().availableProcessors());
    }
 
    /** Custom thread pool for controlled parallelism. */
    public ParallelExecutor(ForkJoinPool pool, FailureMode failureMode) {
        this(pool, failureMode, Runtime.getRuntime().availableProcessors());
    }
    public ParallelExecutor(ForkJoinPool pool, FailureMode failureMode, int maxConcurrency) {
        this.pool = Objects.requireNonNull(pool);
        this.failureMode = Objects.requireNonNull(failureMode);
        this.maxConcurrency = maxConcurrency;
        this.limiter = new Semaphore(maxConcurrency);
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
        Set<NodePlugin<?, ?>> inProgress = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> rootFutures = new ArrayList<>();
        for (NodePlugin<?, ?> root : graph.getRootNodes()) {
        	if (inProgress.add(root))
        		rootFutures.add(
        				executeNodeAsync(root, input, graph, context, nodeOutputs, pendingIncoming, inProgress, cyclicTargets)
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
    /**
     * Convenience method for graphs with exactly one terminal node.
     *
     * @throws IllegalStateException if the graph has zero or multiple terminal nodes.
     */
    @SuppressWarnings("unchecked")
    public <O> O runSingle(Graph graph, Object input, NodeContext context) {
        Set<NodePlugin<?, ?>> terminals = graph.getTerminalNodes();
        if (terminals.size() != 1) {
            throw new IllegalStateException(
                "runSingle() requires exactly 1 terminal node, but graph [" + graph.getName()
                    + "] has " + terminals.size() + ": "
                    + terminals.stream().map(NodePlugin::getName).collect(Collectors.joining(", "))
                    + ". Use run() instead."
            );
        }
        Map<String, Object> results = run(graph, input, context);
        Object result = results.values().iterator().next();
        if (result == null) {
            throw new IllegalStateException(
                "Terminal node returned null. Possible failure or skipped execution."
            );
        }
        return (O) result;
    }
    
    @SuppressWarnings("unchecked")
    private CompletableFuture<Void> executeNodeAsync(
            NodePlugin<?, ?> node,
            Object input,
            Graph graph,
            NodeContext context,
            Map<NodePlugin<?, ?>, Object> nodeOutputs,
            Map<NodePlugin<?, ?>, Integer> pendingIncoming,
            Set<NodePlugin<?, ?>> inProgress,
            Set<NodePlugin<?, ?>> cyclicTargets
    ) {
        return CompletableFuture.supplyAsync(() -> {
        	boolean acquired = false;
        	try {
        		limiter.acquire();
        		acquired = true;
        		context.getLogger().info("[DAG] Executing: " + node.getName());
        		NodePlugin<Object, Object> safeNode = (NodePlugin<Object, Object>) node;
        		safeNode.onStart(context);
                Object output = safeNode.process(input, context);
                safeNode.onFinish(context);
                context.getLogger().info("[DAG] Completed: " + node.getName());
                nodeOutputs.put(node, output);
                return output;
            } catch (Exception e) {
                context.getLogger().severe("[DAG] Error in node [" + node.getName() + "]: " + e.getMessage());
                try { context.getExceptionHandler().handleException((NodePlugin<Object,Object>)node, e, context); } catch (Exception ignored) {}
                try { ((NodePlugin<Object,Object>)node).onError(e, context); } catch (Exception ignored) {}
                context.setMetadata("failed:" + node.getName(), true);
                if (failureMode == FailureMode.FAIL_FAST) {
                	throw new RuntimeException("Node failed: " + node.getName(), e);
                }
                // CONTINUE_ON_ERROR mode -> skip downstream execution
                return null;
            } finally {
            	if (acquired) {
            		limiter.release();
            	}
            	inProgress.remove(node);
            }           
        }, pool).thenCompose(output -> {
        	if (output == null) {
        		return CompletableFuture.completedFuture(null);
        	}
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
                    executeNodeAsync(target, output, graph, context, nodeOutputs, pendingIncoming, inProgress, cyclicTargets)
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
enum FailureMode{
	FAIL_FAST,
	CONTINUE_ON_ERROR
}