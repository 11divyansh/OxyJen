package io.oxyjen.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import io.oxyjen.core.Edge;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.graph.branching.BranchNode;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.validation.DAGValidator;

public class ParallelExecutor {
	
	private final ExecutionRuntime runtime;
	 
    /** Default: uses the common pool.*/
    public ParallelExecutor() {
        this(ExecutionRuntime.defaultRuntime());
    }
 
    /** Custom thread pool for controlled parallelism. */
    public ParallelExecutor(ExecutionRuntime runtime) {
        this.runtime = runtime;
    }
   
    public record NodeFailure(String nodeName, Throwable error) {}
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
        context.setRuntime(runtime);
        context.setMetadata("graphName", graph.getName());
 
        // nodeOutput[node] = the output it produced (filled as nodes complete)
        Map<String, Optional<Object>> nodeOutputs = new ConcurrentHashMap<>();
        Set<String> scheduled = ConcurrentHashMap.newKeySet();
        Set<NodePlugin<?, ?>> cyclicTargets = findCyclicTargets(graph);
        Set<NodePlugin<?, ?>> inProgress = ConcurrentHashMap.newKeySet();
        Set<CompletableFuture<?>> allFutures = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> rootFutures = new ArrayList<>();
        // register merge nodes
        for (NodePlugin<?, ?> node : graph.getNodes()) {
        	NodePlugin<?, ?> actual = node.unwrap();
            if (actual instanceof MergeNode merge) {
                merge.register(context);
            }
        }
        for (NodePlugin<?, ?> root : graph.getRootNodes()) {
        	if (scheduled.add(root.getName()))
        		rootFutures.add(
        				executeNodeAsync(root, input, graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures)
        		);
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            rootFutures.toArray(new CompletableFuture[0])
        );
        try {
            while (true) {
                CompletableFuture<?>[] snapshot =
                    allFutures.toArray(new CompletableFuture[0]);
                CompletableFuture.allOf(snapshot).join();
                if (allFutures.size() == snapshot.length) {
                    break;
                }
            }
        } catch (CompletionException e) {
            Throwable cause = e;
            while ((cause instanceof CompletionException || cause instanceof ExecutionException || cause instanceof RuntimeException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }

            if (cause instanceof MergeNode.MergeTimeoutException timeout) throw timeout;
            if (cause instanceof RuntimeException re) throw re;

            throw new RuntimeException("Graph execution failed: " + graph.getName(), cause);
        }
        context.getLogger().info(
        	    "[DEBUG] Terminal nodes: " +
        	    graph.getTerminalNodes()
        	        .stream()
        	        .map(n -> n.getName() + " (unwrap=" + n.unwrap().getName() + ")")
        	        .toList()
        	);
        context.getLogger().info(
        	    "[DEBUG] nodeOutputs keys: " + nodeOutputs.keySet()
        	);
        Map<String, Object> results = new LinkedHashMap<>();
        for (NodePlugin<?, ?> terminal : graph.getTerminalNodes()) {
        	//NodePlugin<?, ?> actual = terminal.unwrap();
        	String name = terminal.getName();
            String unwrapName = terminal.unwrap().getName();
            Optional<Object> value = nodeOutputs.get(name);
            Object actual = value != null ? value.orElse(null) : null;
        	context.getLogger().info(
        		       "[DEBUG] Reading terminal → name=" + name +
        		       ", unwrap=" + unwrapName +
        		       ", value=" + actual
        		   );
            results.put(terminal.getName(), actual);
        }
        if (results.isEmpty()) {
            throw new IllegalStateException(
                "Graph produced no outputs. Possible causes: no terminal nodes or all branches skipped."
            );
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
        NodePlugin<?, ?> terminal = terminals.iterator().next();
        Object result = results.get(terminal.getName());
        if (result == null) {
            throw new IllegalStateException(
                "Terminal node [" + terminal.getName() + "] returned null. Possible failure or skipped execution."
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
            Map<String, Optional<Object>> nodeOutputs,
            Set<NodePlugin<?, ?>> inProgress,
            Set<String> scheduled,
            Set<NodePlugin<?, ?>> cyclicTargets,
            Set<CompletableFuture<?>> allFutures
    ) {
    	Semaphore limiter = runtime.getLimiter();
    	try {
    	    limiter.acquire();
    	} catch (InterruptedException e) {
    	    Thread.currentThread().interrupt();
    	    throw new RuntimeException("[DAG] Interrupted waiting for limiter: " + node.getName(), e);
    	}
    	CompletableFuture<Void> future = CompletableFuture.<Object>supplyAsync(() -> {
        	//boolean acquired = false;
        	try {
        		NodePlugin<Object, Object> safeNode = (NodePlugin<Object, Object>) node;
        		context.getLogger().info("[DAG] Executing: " + node.getName());
        		safeNode.onStart(context);
                Object output = safeNode.process(input, context);
                safeNode.onFinish(context);
                context.getLogger().info("[DAG] Completed: " + node.getName());
                if (output instanceof BranchNode.RoutedResult routed) {
                	nodeOutputs.put(node.getName(), Optional.ofNullable(routed.output()));
                	return routed;
                }
                nodeOutputs.put(node.getName(), Optional.ofNullable(output));
                return output;
            } catch (Exception e) {
            	if (!(e instanceof MergeNode.MergeTimeoutException)) {
            	    context.getLogger().severe("[DAG] Error in node [" + node.getName() + "]: " + e.getMessage());
            	}
                //context.getLogger().severe("[DAG] Error in node [" + node.getName() + "]: " + e.getMessage());
                try { context.getExceptionHandler().handleException((NodePlugin<Object,Object>)node, e, context); } catch (Exception ignored) {}
                try { ((NodePlugin<Object,Object>)node).onError(e, context); } catch (Exception ignored) {}
                context.setMetadata("failed:" + node.getName(), true);
                ExecutionRuntime runtime = context.getRuntime();
                ExecutionRuntime.FailureMode mode = runtime.getFailureMode();
                switch (mode) {
                    case FAIL_FAST -> {
                        // stop everything
                        throw new RuntimeException("Node failed: " + node.getName(), e);
                    }

                    case COLLECT_ERRORS -> {
                    	if (node.unwrap() instanceof MergeNode && e instanceof MergeNode.MergeTimeoutException) {
                            throw new CompletionException(e);
                        }
                        // continue graph but preserve error
                    	NodeFailure failure = new NodeFailure(node.getName(), e);
                        nodeOutputs.put(node.getName(), Optional.ofNullable(failure));
                        return failure;
                    }
                    
                    case SKIP_FAILED -> {
                        // skip this node's downstream
                        return null;
                    }
                }
                return null; // fallback
            } finally {
            	limiter.release();
            }           
        }, runtime.getExecutor()).thenCompose(output -> {
            if (output == null) {
                inProgress.remove(node);
                return CompletableFuture.completedFuture(null);
            }
            if (output instanceof NodeFailure failure) {
                context.getLogger().warning(
                    "[DAG] Node failed but continuing: " + failure.nodeName()
                );
            }
            if (output instanceof BranchNode.RoutedResult routed) {
                NodePlugin<?, ?> target = graph.findNodeByName(routed.nextNode());
                CompletableFuture<Void> branch = executeNodeAsync(target, routed.output(), graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures);
                CompletableFuture<Void> composed = branch.thenRun(() -> inProgress.remove(node));
                allFutures.add(composed);
                return composed;
            }
            if (output instanceof RouterNode.RoutedResult routed) {
            	List<CompletableFuture<Void>> routerFutures = new ArrayList<>();
                for (Map.Entry<String, Object> entry : routed.routes().entrySet()) {
                    NodePlugin<?, ?> target = graph.findNodeByName(entry.getKey());
                    if (scheduled.add(target.getName())) {
                        routerFutures.add(executeNodeAsync(target, entry.getValue(), graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures));
                    }
                }
                if (!routerFutures.isEmpty()) {
                    CompletableFuture<Void> composed = CompletableFuture.allOf(routerFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> inProgress.remove(node));
                    allFutures.add(composed);
                    return composed;
                }
                inProgress.remove(node);
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> downstream = new ArrayList<>();
            boolean traversedCycle = false;
            for (Edge edge : graph.getEdgesFrom(node)) {
                if (!(edge instanceof CyclicEdge)) {
                    continue;
                }
                boolean decision = (output instanceof NodeFailure failure)
                        ? edge.shouldTraverseFailure(failure, context)
                        : edge.shouldTraverse(output, context);
                if (!decision) continue;
                NodePlugin<?, ?> target = edge.getTarget();
                traversedCycle = true;
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures)
                );
            }
            if (!traversedCycle) {
            for (Edge edge : graph.getEdgesFrom(node)) {
                if (edge instanceof CyclicEdge) {
                    continue;
                }
                boolean decision = (output instanceof NodeFailure failure)
                        ? edge.shouldTraverseFailure(failure, context)
                        : edge.shouldTraverse(output, context);
                if (!decision) continue;
                NodePlugin<?, ?> target = edge.getTarget();
                NodePlugin<?, ?> actualTarget = target.unwrap();
                if (actualTarget instanceof MergeNode merge && !(node.unwrap() instanceof MergeNode)) {
                    merge.contribute(node.getName(), output, context);
                    if (scheduled.add(merge.getName())) {
                        downstream.add(
                            executeNodeAsync(target, null, graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures)
                        );
                    }
                    continue;
                }
                //if (!decision) continue;
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, inProgress, scheduled, cyclicTargets, allFutures)
                );
            }
            }
            if (!downstream.isEmpty()) {
            	CompletableFuture<Void> composed = CompletableFuture
            	        .allOf(downstream.toArray(new CompletableFuture[0]))
            	        .thenRun(() -> inProgress.remove(node));
            	allFutures.add(composed);
            	return composed;
            }
            inProgress.remove(node);
            return CompletableFuture.completedFuture(null);
        });
    	allFutures.add(future);
    	return future; 
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
