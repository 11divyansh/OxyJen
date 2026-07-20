package io.oxyjen.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.oxyjen.execution.ExecutionTimeline.MutableNodeState;
import io.oxyjen.execution.metrics.NodeMetrics;
import io.oxyjen.observe.ObservationListener;

public final class ExecutionTimeline implements ObservationListener {

	private final String executionId;
	 
    /** Raw event log, source of truth for replay. */
    private final ConcurrentLinkedQueue<ExecutionEvent> events = new ConcurrentLinkedQueue<>();
 
    /** Workflow-level start time, set on WorkflowStarted. */
    private volatile Instant workflowStartedAt;
 
    /** Workflow-level finish time, set on WorkflowFinished/Cancelled/Suspended. */
    private volatile Instant workflowFinishedAt;
 
    /** Terminal workflow status. */
    private volatile ExecutionStatus workflowStatus = ExecutionStatus.RUNNING;
 
    public ExecutionTimeline(String executionId) {
        this.executionId = executionId;
    }
 
    @Override
    public void onEvent(ExecutionEvent event) {
        if (!event.executionId().equals(executionId)) return;
 
        events.add(event);
 
        switch (event) {
            case ExecutionEvent.WorkflowStarted e -> workflowStartedAt = e.at();
 
            case ExecutionEvent.WorkflowFinished e -> {
                workflowFinishedAt = e.at();
                workflowStatus = e.status();
            }
            case ExecutionEvent.ExecutionCancelled e -> {
                workflowFinishedAt = e.at();
                workflowStatus = ExecutionStatus.CANCELLED;
            }
            case ExecutionEvent.ExecutionSuspended e -> {
                workflowFinishedAt = e.at();
                workflowStatus = ExecutionStatus.SUSPENDED;
            }
            case ExecutionEvent.ExecutionResumed ignored -> workflowStatus = ExecutionStatus.RUNNING;
 
            case ExecutionEvent.NodeStarted e -> {
                nodeStates.compute(e.nodeId(), (id, existing) -> {
                    if (existing == null) existing = new MutableNodeState(id, executionId);
                    existing.startedAt = e.at();
                    existing.status = NodeStatus.RUNNING;
                    existing.attempts = e.attempt();
                    return existing;
                });
            }
            case ExecutionEvent.NodeCompleted e -> {
                nodeStates.compute(e.nodeId(), (id, existing) -> {
                    if (existing == null) existing = new MutableNodeState(id, executionId);
                    existing.finishedAt = e.at();
                    existing.status = NodeStatus.COMPLETED;
                    existing.metrics = e.metrics();
                    return existing;
                });
            }
            case ExecutionEvent.NodeFailed e -> {
                nodeStates.compute(e.nodeId(), (id, existing) -> {
                    if (existing == null) existing = new MutableNodeState(id, executionId);
                    existing.finishedAt = e.at();
                    existing.status = NodeStatus.FAILED;
                    existing.failures.add(new NodeExecution.FailureRecord(
                            e.attempt(), e.failure(), e.at()));
                    return existing;
                });
            }
            case ExecutionEvent.RetryAttempt e -> {
                nodeStates.compute(e.nodeId(), (id, existing) -> {
                    if (existing == null) existing = new MutableNodeState(id, executionId);
                    existing.attempts = e.attempt();
                    existing.failures.add(new NodeExecution.FailureRecord(
                            e.attempt() - 1, e.failure(), e.at()));
                    return existing;
                });
            }
            case ExecutionEvent.NodeSkipped e -> {
                nodeStates.compute(e.nodeId(), (id, existing) -> {
                    if (existing == null) existing = new MutableNodeState(id, executionId);
                    existing.status = NodeStatus.SKIPPED;
                    existing.finishedAt = e.at();
                    return existing;
                });
            }
            default -> { /* recorded in events list above, no fold needed */ }
        }
    }
    
    static final class MutableNodeState {
        final String nodeId;
        final String executionId;
        final List<NodeExecution.AttemptFailure> failures = new ArrayList<>();
 
        Instant startedAt;
        Instant finishedAt;
        NodeStatus status = NodeStatus.PENDING;
        int attempts = 0;
        NodeMetrics metrics;
 
        MutableNodeState(String nodeId, String executionId) {
            this.nodeId = nodeId;
            this.executionId = executionId;
        }
 
        NodeExecution toNodeExecution() {
            return new NodeExecution(
                    executionId,
                    nodeId,
                    Optional.ofNullable(startedAt),
                    Optional.ofNullable(finishedAt),
                    status,
                    attempts,
                    Optional.ofNullable(metrics),
                    List.copyOf(failures)
            );
        }
    }
}