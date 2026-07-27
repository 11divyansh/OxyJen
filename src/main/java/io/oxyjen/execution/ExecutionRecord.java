package io.oxyjen.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable point-in-time snapshot of a workflow execution.
 *
 ** <p>Produced by {@link ExecutionTimeline#snapshot()} and consumed by
 * {@link io.oxyjen.persist.ExecutionStore} for persistence, by
 * {@link io.oxyjen.replay.ReplayEngine} for replay, and by
 * {@link io.oxyjen.replay.ExecutionDiff} for comparing two runs.
 *
 * <p>Carries two complementary views of the same execution:
 * <ul>
 *   <li>{@link #events()} - the raw ordered event sequence. This is the
 *       source of truth: replay reconstructs execution by replaying these
 *       events in order.</li>
 *   <li>{@link #nodeExecutions()} - per-node aggregated view, pre-folded
 *       from the event sequence. Exporters and dashboards read from here
 *       rather than scanning the raw event log every time.</li>
 * </ul>
 * 
 * <p>{@link #workflowId()} is promoted to a top-level field (not extracted from
 * events at query time) so persistence backends can index it as a column/field
 * without deserializing the full event log for every query.
 *
 * <p>Both views are immutable copies taken at the moment
 * {@link ExecutionTimeline#snapshot()} was called. Events that arrive
 * after the snapshot are not reflected here.
 * 
 * @param schemaVersion   version of the serialization schema, increment when
 *                        {@link ExecutionRecord}'s serialized shape changes so
 *                        backends can migrate old records. Currently {@code 1}.
 * @param executionId     unique identifier for this workflow run
 * @param workflowId      identifier of the workflow definition that was run;
 *                        promoted to top level for efficient querying
 * @param status          workflow-level status at snapshot time
 * @param startedAt       when the workflow started; {@code null} if
 *                        {@code WorkflowStarted} was never emitted
 * @param finishedAt      when the workflow reached a terminal state;
 *                        {@code null} if still running at snapshot time
 * @param events          raw ordered event log, source of truth for replay
 * @param nodeExecutions  per-node aggregated execution records, keyed by nodeId
 */
public record ExecutionRecord(
		int schemaVersion,
        String executionId,
        String workflowId,
        ExecutionStatus status,
        Instant startedAt,
        Instant finishedAt,
        List<ExecutionEvent> events,
        Map<String, NodeExecution> nodeExecutions
) {
	
	/** Current schema version. Increment when the serialized shape changes. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    
    public ExecutionRecord {
    	if (schemaVersion < 1) 
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        if (executionId == null || executionId.isBlank())
            throw new IllegalArgumentException("executionId must not be blank");
        if (workflowId == null || workflowId.isBlank()) 
            throw new IllegalArgumentException("workflowId must not be blank");
        if (status == null)
            throw new IllegalArgumentException("status must not be null");
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) 
            throw new IllegalArgumentException("finishedAt must not be before startedAt");

        validateStatusConsistency(status, startedAt, finishedAt);
        events = List.copyOf(events);
        nodeExecutions = Map.copyOf(nodeExecutions);
    }
    
    /**
     * Convenience constructor that fills in the current schema version.
     * Use this in production, the full constructor is for deserialization only.
     */
    public ExecutionRecord(
            String executionId,
            String workflowId,
            ExecutionStatus status,
            Instant startedAt,
            Instant finishedAt,
            List<ExecutionEvent> events,
            Map<String, NodeExecution> nodeExecutions
    ) {
        this(CURRENT_SCHEMA_VERSION, executionId, workflowId, status, startedAt, finishedAt, events, nodeExecutions);
    }
    
    private static void validateStatusConsistency(
            ExecutionStatus status,
            Instant startedAt,
            Instant finishedAt
    ) {

        switch (status) {
            case RUNNING -> {
                if (finishedAt != null) 
                    throw new IllegalArgumentException("RUNNING execution must not have finishedAt");
            }

            case COMPLETED, FAILED, CANCELLED -> {
                if (finishedAt == null) 
                    throw new IllegalArgumentException(status + " execution must have finishedAt");
            }

            case SUSPENDED -> {
                // Suspended executions are paused and therefore have a
                // suspension timestamp, but may later be resumed.
                if (finishedAt == null) 
                    throw new IllegalArgumentException("SUSPENDED execution must have finishedAt");
            }
        }

        if (startedAt == null && status != ExecutionStatus.RUNNING) 
        	throw new IllegalArgumentException("Non-running executions must have startedAt");
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