package io.oxyjen.graph;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import io.oxyjen.execution.ExecutionEvent;
import io.oxyjen.execution.ExecutionMetadataKeys;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.ExecutionStatus;
import io.oxyjen.execution.FailureInfo;
import io.oxyjen.execution.metrics.NodeMetrics;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;
import io.oxyjen.graph.edges.CyclicEdge;
import io.oxyjen.graph.edges.FailureEdge;
import io.oxyjen.graph.edges.RouteEdge;
import io.oxyjen.graph.validation.DAGValidator;
import io.oxyjen.llm.UsesRuntimeLimiter;
import io.oxyjen.observe.ObservationBus;

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
 
        // assign executionId for this run
        String executionId = UUID.randomUUID().toString();
        context.setMetadata("executionId", executionId);
        ObservationBus bus = runtime.observationBus();
        Instant workflowStarted = Instant.now();
 
        // emit WorkflowStarted
        emit(bus, new ExecutionEvent.WorkflowStarted(
                executionId,
                workflowStarted,
                graph.getName(),
                Map.of() // TODO v1: will replace with typed ExecutionContextSnapshot
        ));
        // nodeOutput[node] = the output it produced (filled as nodes complete)
        Map<String, Optional<Object>> nodeOutputs = new ConcurrentHashMap<>();
        Map<String, Throwable> nodeFailures = new ConcurrentHashMap<>();
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
        				executeNodeAsync(root, input, graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus)
        		);
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(
            rootFutures.toArray(new CompletableFuture[0])
        );
        ExecutionStatus finalStatus = ExecutionStatus.COMPLETED;
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
        	finalStatus = ExecutionStatus.FAILED;
            Throwable cause = e;
            while ((cause instanceof CompletionException || cause instanceof ExecutionException || cause instanceof RuntimeException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            
            // emit WorkflowFinished (FAILED)
            emit(bus, new ExecutionEvent.WorkflowFinished(
                    executionId,
                    Instant.now(),
                    ExecutionStatus.FAILED,
                    Duration.between(workflowStarted, Instant.now()).toMillis()
            ));

            if (cause instanceof MergeNode.MergeTimeoutException timeout) throw timeout;
            if (cause instanceof RuntimeException re) throw re;

            throw new RuntimeException("Graph execution failed: " + graph.getName(), cause);
        }
        
        // emit WorkflowFinished (COMPLETED)
        emit(bus, new ExecutionEvent.WorkflowFinished(
                executionId,
                Instant.now(),
                ExecutionStatus.COMPLETED,
                Duration.between(workflowStarted, Instant.now()).toMillis()
        ));
        context.getLogger().info(
        	    "[DEBUG] Terminal nodes: " +
        	    graph.getTerminalNodes()
        	        .stream()
        	        .map(n -> n.getName() + " (unwrap=" + n.unwrap().getName() + ")")
        	        .toList()
        	);
        context.getLogger().info("[DEBUG] nodeOutputs keys: " + nodeOutputs.keySet());
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
            Map<String, Throwable> nodeFailures,
            Set<NodePlugin<?, ?>> inProgress,
            Set<String> scheduled,
            Set<NodePlugin<?, ?>> cyclicTargets,
            Set<CompletableFuture<?>> allFutures,
            String executionId,
            ObservationBus bus
    ) {
    	Semaphore limiter = runtime.getLimiter();
    	NodePlugin<?, ?> unwrappedNode = node.unwrap();
    	boolean isIO = unwrappedNode instanceof UsesRuntimeLimiter;
    	NodePlugin<Object, Object> actualNode = (NodePlugin<Object, Object>) unwrappedNode;
    	if (isIO) {
    		try {
    			limiter.acquire();
    		} catch (InterruptedException e) {
    			Thread.currentThread().interrupt();
    			throw new RuntimeException("[DAG] Interrupted waiting for limiter: " + node.getName(), e);
    		}
    	}
    	
    	CompletableFuture<Void> future = CompletableFuture.<Object>supplyAsync(() -> {
    		Instant nodeStart = Instant.now();
            int attempt = 1;
            String nodeId = node.getName();
 
            // emit NodeStarted
            emit(bus, new ExecutionEvent.NodeStarted(executionId, nodeStart, nodeId, attempt));
            ExecutionMetadataKeys.setCurrentNodeId(nodeId);
        	try {
                context.getLogger().info("[DAG] Executing: " + nodeId);
                actualNode.onStart(context);
                Object output = actualNode.process(input, context);
                actualNode.onFinish(context);
                context.getLogger().info("[DAG] Completed: " + nodeId);
                Duration duration = Duration.between(nodeStart, Instant.now());
                NodeMetrics metrics = resolveNodeMetrics(context, nodeId, duration);
                emit(bus, new ExecutionEvent.NodeCompleted(
                        executionId,
                        Instant.now(),
                        nodeId,
                        metrics
                ));
                if (actualNode instanceof RouterNode) {
                    nodeOutputs.put(nodeId, Optional.ofNullable(output));
                    return output;
                }
                nodeOutputs.put(nodeId, Optional.ofNullable(output));
                return output;
            } catch (Exception e) {
            	context.removeMetadata(ExecutionMetadataKeys.nodeMetricsKey(nodeId));
            	// emit NodeFailed
                emit(bus, new ExecutionEvent.NodeFailed(
                        executionId,
                        Instant.now(),
                        nodeId,
                        FailureInfo.from(e),
                        attempt
                ));
            	if (!(e instanceof MergeNode.MergeTimeoutException)) {
            	    context.getLogger().severe("[DAG] Error in node [" + nodeId + "]: " + e.getMessage());
                }
                try { context.getExceptionHandler().handleException(actualNode, e, context); } catch (Exception ignored) {}
                try { actualNode.onError(e, context); } catch (Exception ignored) {}
                context.setMetadata("failed:" + nodeId, true);
                ExecutionRuntime runtime = context.getRuntime();
                ExecutionRuntime.FailureMode mode = runtime.getFailureMode();
                switch (mode) {
                    case FAIL_FAST -> {
                        // stop everything
                        throw new RuntimeException("Node failed: " + actualNode.getName(), e);
                    }

                    case COLLECT_ERRORS -> {
                    	if (node.unwrap() instanceof MergeNode && e instanceof MergeNode.MergeTimeoutException) {
                            throw new CompletionException(e);
                        }
                        // continue graph but preserve error
                    	nodeFailures.put(node.getName(), e);
                        nodeOutputs.put(node.getName(), Optional.ofNullable(e));
                        return e;
                    }
                    
                    case SKIP_FAILED -> {
                        // skip this node's downstream
                    	// emit NodeSkipped for all downstream
                        for (Edge edge : graph.getEdgesFrom(node)) {
                            emit(bus, new ExecutionEvent.NodeSkipped(
                                    executionId,
                                    Instant.now(),
                                    edge.getTarget().getName(),
                                    "upstream node failed: " + node.getName()
                            ));
                        }
                        return null;
                    }
                }
                return null; // fallback
            } finally {
            	ExecutionMetadataKeys.clearCurrentNodeId();
            	if (isIO) limiter.release();
            }           
        }, runtime.getExecutor()).thenCompose(output -> {
            Throwable failure = nodeFailures.get(node.getName());
            if (failure != null) {
                context.getLogger().warning(
                    "[DAG] Node failed but continuing: " + node.getName()
                );
            }
            if (node.unwrap() instanceof io.oxyjen.graph.branching.BranchNode<?>) {
                String nextNode = context.getMetadata(io.oxyjen.graph.branching.BranchNode.ROUTE_KEY_PREFIX + node.getName());
                if (nextNode == null) {
                    inProgress.remove(node);
                    return CompletableFuture.completedFuture(null);
                }
                // emit BranchTaken
                emit(bus, new ExecutionEvent.BranchTaken(executionId, Instant.now(), node.getName(), nextNode));
                NodePlugin<?, ?> target = graph.findNodeByName(nextNode);
                CompletableFuture<Void> branch = executeNodeAsync(target, output, graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus);
                CompletableFuture<Void> composed = branch.thenRun(() -> inProgress.remove(node));
                allFutures.add(composed);
                return composed;
            }
            if (output == null) {
                inProgress.remove(node);
                return CompletableFuture.completedFuture(null);
            }
            if (node.unwrap() instanceof RouterNode) {
                Map<String, Object> routes = (Map<String, Object>) output;
            	List<CompletableFuture<Void>> routerFutures = new ArrayList<>();
            	// emit ParallelStarted
                emit(bus, new ExecutionEvent.ParallelStarted(executionId, Instant.now(), node.getName(), routes.size()));
                for (Map.Entry<String, Object> entry : routes.entrySet()) {
                    NodePlugin<?, ?> target = graph.findNodeByName(entry.getKey());
                    if (scheduled.add(target.getName())) {
                        routerFutures.add(executeNodeAsync(target, entry.getValue(), graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus));
                    }
                }
                
                for (Edge edge : graph.getEdgesFrom(node)) {
                    if (edge instanceof FailureEdge || edge instanceof CyclicEdge) continue;
                    if (edge instanceof RouteEdge) continue; // handled by routes map above
                    NodePlugin<?, ?> target = edge.getTarget();
                    if (routes.containsKey(target.getName())) continue; // already handled above
                    // direct edge targets from RouterNode are always unique and should always execute
                    scheduled.add(target.getName()); // mark as scheduled
                    routerFutures.add(executeNodeAsync(
                    	target, input, graph, context,
                    	nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus
                    ));
                }
                if (!routerFutures.isEmpty()) {
                	Instant parallelStart = Instant.now();
                    CompletableFuture<Void> composed = CompletableFuture.allOf(routerFutures.toArray(new CompletableFuture[0]))
                        .thenRun(() -> {
                        	// emit ParallelCompleted
                            emit(bus, new ExecutionEvent.ParallelCompleted(
                                    executionId,
                                    Instant.now(),
                                    node.getName(),
                                    routerFutures.size(),
                                    0, // individual failures captured per-node above
                                    Duration.between(parallelStart, Instant.now()).toMillis()
                            ));
                            inProgress.remove(node);
                        });
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
                boolean decision = (failure != null)
                        ? edge.shouldTraverseFailure(failure, context)
                        : edge.shouldTraverse(output, context);
                if (!decision) continue;
                NodePlugin<?, ?> target = edge.getTarget();
                traversedCycle = true;
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus)
                );
            }
            if (!traversedCycle) {
            for (Edge edge : graph.getEdgesFrom(node)) {
                if (edge instanceof CyclicEdge) {
                    continue;
                }
                boolean decision = (failure != null)
                        ? edge.shouldTraverseFailure(failure, context)
                        : edge.shouldTraverse(output, context);
                if (!decision) {
                	// emit NodeSkipped for edges not traversed
                    emit(bus, new ExecutionEvent.NodeSkipped(
                            executionId,
                            Instant.now(),
                            edge.getTarget().getName(),
                            "edge condition not satisfied from: " + node.getName()
                    ));
                	continue;
                }
                NodePlugin<?, ?> target = edge.getTarget();
                NodePlugin<?, ?> actualTarget = target.unwrap();
                if (actualTarget instanceof MergeNode merge && !(node.unwrap() instanceof MergeNode)) {
                    if (failure != null) {
                        merge.contributeFailure(node.getName(), failure, context);
                    } else {
                        merge.contribute(node.getName(), output, context);
                    }
                    if (scheduled.add(merge.getName())) {
                        downstream.add(
                            executeNodeAsync(target, null, graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus)
                        );
                    }
                    continue;
                }
                //if (!decision) continue;
                downstream.add(
                    executeNodeAsync(target, output, graph, context, nodeOutputs, nodeFailures, inProgress, scheduled, cyclicTargets, allFutures, executionId, bus)
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
    
    private static void emit(ObservationBus bus, ExecutionEvent event) {
    	if (!bus.isEmpty()) {
    		bus.emit(event);
    	}
    }

    private NodeMetrics resolveNodeMetrics(NodeContext context, String nodeId, Duration fallbackDuration) {
        Object stored = context.removeMetadata(ExecutionMetadataKeys.nodeMetricsKey(nodeId));
        if (stored instanceof NodeMetrics metrics) {
            return metrics;
        }
        return NodeMetrics.GraphNodeMetrics.of(fallbackDuration);
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
