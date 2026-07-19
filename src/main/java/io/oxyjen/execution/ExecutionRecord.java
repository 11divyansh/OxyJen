package io.oxyjen.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable point-in-time snapshot of a workflow execution.
 *
 * @param executionId     unique identifier for this workflow run
 * @param status          workflow-level status at snapshot time
 * @param startedAt       when the workflow started; {@code null} if
 *                        {@code WorkflowStarted} was never emitted
 * @param finishedAt      when the workflow reached a terminal state;
 *                        {@code null} if still running at snapshot time
 * @param events          raw ordered event log, source of truth for replay
 * @param nodeExecutions  per-node aggregated execution records, keyed by nodeId
 */
public record ExecutionRecord(
        String executionId,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<ExecutionEvent> events,
        Map<String, NodeExecution> nodeExecutions
) {

    public ExecutionRecord {
        if (executionId == null || executionId.isBlank())
            throw new IllegalArgumentException("executionId must not be blank");
        if (status == null)
            throw new IllegalArgumentException("status must not be null");
        events = List.copyOf(events);
        nodeExecutions = Map.copyOf(nodeExecutions);
    }

    /** Total wall-clock duration of the execution, or empty if not yet finished. */
    public Optional<Duration> duration() {
        if (startedAt == null || finishedAt == null) return Optional.empty();
        return Optional.of(Duration.between(startedAt, finishedAt));
    }

    /** Whether the workflow reached a terminal state. */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /** Whether the workflow completed without failures. */
    public boolean isSuccessful() {
        return status == ExecutionStatus.COMPLETED;
    }

    /**
     * Returns the {@link NodeExecution} for a specific node, or
     * {@link Optional#empty()} if the node never appeared in this execution
     * (e.g. a branch that was never taken and never emitted {@code NodeSkipped}).
     */
    public Optional<NodeExecution> nodeExecution(String nodeId) {
        return Optional.ofNullable(nodeExecutions.get(nodeId));
    }

    /**
     * All events of a specific type, in the order they were emitted.
     * Useful for replay consumers that only care about one event category.
     *
     * <p>Example - get all branch decisions:
     * <pre>{@code
     * record.eventsOfType(ExecutionEvent.BranchTaken.class)
     * }</pre>
     */
    public <E extends ExecutionEvent> List<E> eventsOfType(Class<E> type) {
        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toList();
    }

    /**
     * Total number of node executions that completed successfully.
     * Skipped and failed nodes are not counted.
     */
    public long completedNodeCount() {
        return nodeExecutions.values().stream()
                .filter(n -> n.status() == NodeStatus.COMPLETED)
                .count();
    }

    /**
     * Total number of node executions that failed.
     */
    public long failedNodeCount() {
        return nodeExecutions.values().stream()
                .filter(n -> n.status() == NodeStatus.FAILED)
                .count();
    }

    /**
     * Total number of nodes that were skipped (branch not taken,
     * condition false, upstream failure with SKIP_FAILED mode, etc.).
     */
    public long skippedNodeCount() {
        return nodeExecutions.values().stream()
                .filter(n -> n.status() == NodeStatus.SKIPPED)
                .count();
    }
}