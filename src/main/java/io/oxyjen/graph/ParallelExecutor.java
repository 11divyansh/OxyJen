package io.oxyjen.graph;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
        Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();
        // register merge nodes
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            if (node instanceof MergeNode merge) {
                merge.register(context);
            }
        }
        Set<NodePlugin<?, ?>> cyclicTargets = findCyclicTargets(graph);
        Set<NodePlugin<?, ?>> inProgress = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> rootFutures = new ArrayList<>();
        for (NodePlugin<?, ?> root : graph.getRootNodes()) {
        	if (inProgress.add(root))
        		rootFutures.add(
        				executeNodeAsync(root, input, graph, context, nodeOutputs, inProgress, cyclicTargets)
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
            results.put(terminal.getName(), nodeOutputs.get(terminal.getName()));
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
            Map<String, Object> nodeOutputs,
            Set<NodePlugin<?, ?>> inProgress,
            Set<NodePlugin<?, ?>> cyclicTargets
    ) {
        return CompletableFuture.supplyAsync(() -> {
        	boolean acquired = false;
        	Semaphore limiter = runtime.getLimiter();
        	try {
        		limiter.acquire();
        		acquired = true;
        		context.getLogger().info("[DAG] Executing: " + node.getName());
        		NodePlugin<Object, Object> safeNode = (NodePlugin<Object, Object>) node;
        		safeNode.onStart(context);
                Object output = safeNode.process(input, context);
                safeNode.onFinish(context);
                context.getLogger().info("[DAG] Completed: " + node.getName());
                if (output instanceof BranchNode.RoutedResult routed) {
                	nodeOutputs.put(node.getName(), routed.output());
                	return routed;
                }
                nodeOutputs.put(node.getName(), output);
                return output;
            } catch (Exception e) {
                context.getLogger().severe("[DAG] Error in node [" + node.getName() + "]: " + e.getMessage());
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
                        // ontinue graph but preserve error
                    	NodeFailure failure = new NodeFailure(node.getName(), e);
                        nodeOutputs.put(node.getName(), failure);
                        return failure;
                    }
                    
                    case SKIP_FAILED -> {
                        // skip this node's downstream
                        return null;
                    }
                }
                return null; // fallback
            } finally {
            	if (acquired) {
            		limiter.release();
            	}
            	inProgress.remove(node);
            }           
        }, runtime.getExecutor()).thenCompose(output -> {
        	if (output == null) {
        		return CompletableFuture.completedFuture(null);
        	}
        	if (output instanceof NodeFailure failure) {
        	    context.getLogger().warning(
        	        "[DAG] Node failed but continuing: " + failure.nodeName()
        	    );
        	}
        	if (output instanceof BranchNode.RoutedResult routed) {
        		 context.getLogger().info(
        				 "[BranchNode] Routing -> " + routed.nextNode()
                 );
                 NodePlugin<?, ?> target = graph.findNodeByName(routed.nextNode());
                 if (target == null) {
                	 throw new IllegalStateException(
                			 "BranchNode [" + node.getName() + "] routed to unknown node: " + routed.nextNode()
                     );
                 }
                 return executeNodeAsync(
                     target,
                     routed.output(),
                     graph,
                     context,
                     nodeOutputs,
                     inProgress,
                     cyclicTargets
                 );
        	}
        	if (output instanceof RouterNode.RoutedResult routed) {
        		nodeOutputs.put(node.getName(), routed.routes());
        	    List<CompletableFuture<Void>> futures = new ArrayList<>();
        	    for (Map.Entry<String, Object> entry : routed.routes().entrySet()) {
        	        NodePlugin<?, ?> target = graph.findNodeByName(entry.getKey());
        	        if (target == null) {
        	            throw new IllegalStateException(
        	                "RouterNode routed to unknown node: " + entry.getKey()
        	            );
        	        }
        	        futures.add(
        	            executeNodeAsync(
        	                target,
        	                entry.getValue(),
        	                graph,
        	                context,
        	                nodeOutputs,
        	                inProgress,
        	                cyclicTargets
        	            )
        	        );
        	    }
        	    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        	}
        	boolean anyTraversed = false;
            // Fan-out: evaluate all outgoing edges and schedule eligible targets
            List<CompletableFuture<Void>> downstream = new ArrayList<>(); 
            Map<Edge, Boolean> decisions = new IdentityHashMap<>();
            boolean hasCyclic = false;
            boolean shouldContinueLoop = false;
            for (Edge edge : graph.getEdgesFrom(node)) {
            	boolean decision;
            	if (output instanceof NodeFailure failure) {
            		decision = false;
            	} else {
            		decision = edge.shouldTraverse(output, context);
            	}
                decisions.put(edge, decision);
                if (edge instanceof CyclicEdge) {
                    hasCyclic = true;
                    if (decision) {
                        shouldContinueLoop = true;
                    }
                }
            }
            for (Edge edge : graph.getEdgesFrom(node)) {
            	if (hasCyclic) {
            		if (shouldContinueLoop && !(edge instanceof CyclicEdge)) {
                    	continue;
                	}
            		if (!shouldContinueLoop && edge instanceof CyclicEdge) {
                    	continue;
                	}
            	}
                if (!decisions.get(edge)) {
                    context.getLogger().info("[DAG] Skipping edge: " + edge.getLabel());
                    continue;
                } 
                anyTraversed = true;
                NodePlugin<?, ?> target = edge.getTarget();
                if (target instanceof MergeNode merge) {
                	// MergeNode may receive NodeFailure as contribution
                	// future update: make MergeNode handle success + failure separately
                    merge.contribute(node.getName(), output, context);
                    if (merge.getMissingContributors(context).isEmpty()) {
                        if (inProgress.add(merge)) { //prevent duplicate execution
                            downstream.add(
                                executeNodeAsync(
                                    merge,
                                    null, // MergeNode doesn't need input
                                    graph,
                                    context,
                                    nodeOutputs,
                                    inProgress,
                                    cyclicTargets
                                )
                            );
                        }
                    }
                    continue;
                }
                // For CyclicEdge, always allow re-execution but still guard duplicate scheduling
                boolean isCyclic = edge instanceof CyclicEdge;
                if (isCyclic) {
                	if(!inProgress.add(target)) continue;
                }
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, inProgress, cyclicTargets)
                );
            }
            if (!anyTraversed && !graph.getEdgesFrom(node).isEmpty()) {
                context.getLogger().warning(
                    "[DAG] No route matched for node: " + node.getName()
                );
            }
            if (downstream.isEmpty()) return CompletableFuture.completedFuture(null);
            return CompletableFuture.allOf(downstream.toArray(new CompletableFuture[0]));
        });
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