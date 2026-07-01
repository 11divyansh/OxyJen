package io.oxyjen.execution;

/**
 * The status of a single node's execution within a workflow run.
 *
 * <p>Kept separate from {@link ExecutionStatus} (which is workflow-level)
 * because the two hierarchies don't overlap cleanly, a workflow is never
 * {@code SKIPPED}, and a node is never {@code SUSPENDED} independently of its
 * workflow.
 */
public enum NodeStatus {

    /** Node is queued or waiting for upstream nodes to complete. */
    PENDING,

    /** Node is currently executing. */
    RUNNING,
    /**
     * Node was intentionally not executed e.g. a branch not taken,
     * or a conditional whose guard evaluated false.
     */
    SKIPPED,

    /** Node completed successfully. */
    COMPLETED,

    /** Node terminated with an unhandled failure. */
    FAILED
}