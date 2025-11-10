package io.oxyjen.core;

/**
 * The fundamental contract for any Oxyjen Node.
 * 
 * A NodePlugin represent a single unit of computation in an Oxyjen graph.
 * Each node takes an input of type I, performs some logic, and returns an output of type O.
 *
 * @param <I> Input type
 * @param <O> Output type
 */
public interface NodePlugin<I, O> {

    /**
     * Called to process the node's main logic.
     *
     * @param input   Input data for the node.
     * @param context Shared context object containing state, logger, etc.
     * @return The output produced by this node.
     */
    O process(I input, NodeContext context);

    /**
     * @return A unique name or identifier for this node (for debugging, logs, etc.).
     */
    String getName();

    /**
     * Lifecycle hook called before node execution starts.
     * Can be overridden for setup tasks (e.g., opening connections, initializing state).
     */
    default void onStart(NodeContext context) {}

    /**
     * Lifecycle hook called after node execution completes.
     * Can be overridden for cleanup tasks (e.g., closing resources, post-logging).
     */
    default void onFinish(NodeContext context) {}
}
