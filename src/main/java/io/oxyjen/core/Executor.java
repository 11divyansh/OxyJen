package io.oxyjen.core;

/**
 * OxyjenExecutor is the runtime engine that drives the execution
 * of a {@link Graph}. It runs each {@link NodePlugin} in the graph
 * sequentially, passing the output of one node as the input to the next.
 *
 * <p>This is the "orchestrator" of the Oxyjen framework — it takes:
 * <ul>
 *     <li>A {@link Graph} (the blueprint of the pipeline)</li>
 *     <li>An initial input (the first node’s input)</li>
 *     <li>A shared {@link NodeContext} (runtime memory and logger)</li>
 * </ul>
 *
 * <p>For each node, the executor:
 * <ol>
 *     <li>Invokes {@code onStart(context)} — lifecycle hook for setup/logging.</li>
 *     <li>Calls {@code process(input, context)} — executes node logic.</li>
 *     <li>Invokes {@code onFinish(context)} — lifecycle hook for cleanup/logging.</li>
 * </ol>
 *
 * <p>The output of each node becomes the input for the next one.
 * Any exception thrown inside a node is caught, logged, and rethrown
 * as a {@link RuntimeException} to stop execution.
 *
 * <p>This version supports only <b>sequential execution</b> (v0.1).
 * Future versions (v0.2+) will introduce async, DAG, and retry logic.
 */
public class Executor {

    /**
     * Runs the provided {@link Graph} with a given input and shared context.
     *
     * @param graph   The graph defining the sequence of {@link NodePlugin}s to execute.
     * @param input   The initial input for the first node in the graph.
     * @param context The shared {@link NodeContext} available to all nodes during execution.
     * @param <I>     The type of the initial input.
     * @param <O>     The expected output type from the last node.
     * @return The final output produced by the last node in the graph.
     * @throws RuntimeException if any node fails during processing.
     */
	@SuppressWarnings("unchecked")
    public <I, O> O run(Graph graph, I input, NodeContext context) {
        // Ensure graph has at least one node before running.
        graph.validate();
        
        //Set graph name inside context, it will help in logging
        context.setMetadata("graphName", graph.getName());

        // The current result —> initially the input, updated after each node executes.
        Object current = input;

        // Iterate through each node in insertion order.
        for (NodePlugin<?, ?> node : graph.getNodes()) {
            context.getLogger().info("Executing node: " + node.getName());

            // Lifecycle hook —> can be used for setup or initialization.
            node.onStart(context);

            NodePlugin<Object, Object> safeNode = (NodePlugin<Object, Object>) node;
            
            // Run the node and capture its output.
            current = executeNode(safeNode, current, context);

            // Lifecycle hook —> cleanup or resource release.
            node.onFinish(context);

            context.getLogger().info("Completed node: " + node.getName());
        }

        // Return the final result (casted to generic type O).
        return (O) current;
    }

    /**
     * Executes a single node safely and handles any exceptions that occur.
     *
     * @param node    The node to execute.
     * @param input   The input to the node.
     * @param context The shared execution context.
     * @return The node’s output, which will be passed to the next node.
     */
    private Object executeNode(NodePlugin<Object, Object> node, Object input, NodeContext context) {
        try {
            // Execute the node’s business logic.
            return node.process(input, context);
        } catch (Exception e) {
            // Log the failure and wrap it in a runtime exception.
            context.getLogger().severe("Error in node [" + node.getName() + "]: " + e.getMessage());
            throw new RuntimeException("Node failed: " + node.getName(), e);
        }
    }
}
