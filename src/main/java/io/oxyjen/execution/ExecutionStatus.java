package io.oxyjen.execution;

/**
 * The status of a workflow execution at a point in time.
 */
public enum ExecutionStatus {

    /** Execution is actively running; no terminal event has been emitted yet. */
    RUNNING,

    /** Execution reached the end of the graph with no unhandled failures. */
    COMPLETED,

    /** Execution stopped due to an unhandled node failure. */
    FAILED,

    /**
     * Execution is paused awaiting external input (e.g. a human-approval node)
     * and can be resumed via {@code runner.resume(executionId, decision)}.
     */
    SUSPENDED,

    /** Execution was explicitly cancelled before reaching a terminal node. */
    CANCELLED
}