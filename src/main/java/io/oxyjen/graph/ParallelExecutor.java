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
 
        // Completed set - guards against re-executing non-cyclic nodes
        Set<NodePlugin<?, ?>> completed = ConcurrentHashMap.newKeySet();
        return null;
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
}
