package io.oxyjen.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.oxyjen.execution.metrics.NodeMetrics;
import io.oxyjen.observe.ObservationListener;

public final class ExecutionTimeline implements ObservationListener {

	private final String executionId;
	
	/** Raw event log, source of truth for replay. */
    private final ConcurrentLinkedQueue<ExecutionEvent> events = new ConcurrentLinkedQueue<>();
	
	/** Mutable per-node state, keyed by nodeId. */
    private final Map<String, MutableNodeState> nodeStates = new ConcurrentHashMap<>();
    
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
        if (!event.executionId().equals(executionId)) {
            return;
        }

        events.add(event);

        if (event instanceof ExecutionEvent.WorkflowStarted e) {
            workflowStartedAt = e.at();
        }
        else if (event instanceof ExecutionEvent.WorkflowFinished e) {
            workflowFinishedAt = e.at();
            workflowStatus = e.status();
        }
        else if (event instanceof ExecutionEvent.ExecutionCancelled e) {
            workflowFinishedAt = e.at();
            workflowStatus = ExecutionStatus.CANCELLED;
        }
        else if (event instanceof ExecutionEvent.ExecutionSuspended e) {
            workflowFinishedAt = e.at();
            workflowStatus = ExecutionStatus.SUSPENDED;
        }
        else if (event instanceof ExecutionEvent.ExecutionResumed) {
            workflowStatus = ExecutionStatus.RUNNING;
        }

        else if (event instanceof ExecutionEvent.NodeStarted e) {
            nodeStates.compute(e.nodeId(), (id, existing) -> {
                if (existing == null) {
                    existing = new MutableNodeState(id, executionId);
                }
                existing.startedAt = e.at();
                existing.status = NodeStatus.RUNNING;
                existing.attempts = e.attempt();
                return existing;
            });
        }
        else if (event instanceof ExecutionEvent.NodeCompleted e) {
            nodeStates.compute(e.nodeId(), (id, existing) -> {
                if (existing == null) {
                    existing = new MutableNodeState(id, executionId);
                }
                existing.finishedAt = e.at();
                existing.status = NodeStatus.COMPLETED;
                existing.metrics = e.metrics();
                return existing;
            });
        }
        else if (event instanceof ExecutionEvent.NodeFailed e) {
            nodeStates.compute(e.nodeId(), (id, existing) -> {
                if (existing == null) {
                    existing = new MutableNodeState(id, executionId);
                }
                existing.finishedAt = e.at();
                existing.status = NodeStatus.FAILED;
                existing.failures.add(new NodeExecution.AttemptFailure(
                        e.attempt(),
                        e.failure(),
                        e.at()));
                return existing;
            });
        }
        else if (event instanceof ExecutionEvent.RetryAttempt e) {
            nodeStates.compute(e.nodeId(), (id, existing) -> {
                if (existing == null) {
                    existing = new MutableNodeState(id, executionId);
                }
                existing.attempts = e.attempt();
                existing.failures.add(new NodeExecution.AttemptFailure(
                        e.attempt() - 1,
                        e.failure(),
                        e.at()));
                return existing;
            });
        }
        else if (event instanceof ExecutionEvent.NodeSkipped e) {
            nodeStates.compute(e.nodeId(), (id, existing) -> {
                if (existing == null) {
                    existing = new MutableNodeState(id, executionId);
                }
                existing.status = NodeStatus.SKIPPED;
                existing.finishedAt = e.at();
                return existing;
            });
        }

        // BranchTaken, ParallelStarted, ParallelCompleted,
        // CheckpointSaved, ChunkGenerated, etc. are intentionally ignored
        // here because they don't affect the folded node state. They are
        // already preserved in the raw event log.
    }
    
    /** The execution this timeline belongs to. */
    public String executionId() {
        return executionId;
    }
 
    /**
     * Raw ordered event log, every event emitted during this execution,
     * in arrival order. This is the source of truth for replay.
     *
     * <p>Returns an unmodifiable view; safe to call from any thread while
     * execution is still running.
     */
    public List<ExecutionEvent> events() {
        return List.copyOf(events);
    }
 
    /**
     * Per-node aggregated execution records, keyed by nodeId.
     * Reflects the current fold state, nodes still {@link NodeStatus#RUNNING}
     * will not yet have {@code finishedAt} or {@code metrics} set.
     */
    public Map<String, NodeExecution> nodeExecutions() {
        Map<String, NodeExecution> result = new HashMap<>();
        nodeStates.forEach((id, state) -> result.put(id, state.toNodeExecution()));
        return Collections.unmodifiableMap(result);
    }
    
    /**
     * Returns the {@link NodeExecution} for a specific node, or
     * {@link Optional#empty()} if no events have been received for it yet.
     */
    public Optional<NodeExecution> nodeExecution(String nodeId) {
        MutableNodeState state = nodeStates.get(nodeId);
        return state == null ? Optional.empty() : Optional.of(state.toNodeExecution());
    }
 
    /** Current workflow-level status. */
    public ExecutionStatus workflowStatus() {
        return workflowStatus;
    }
 
    /** When the workflow started, or {@code null} if not yet started. */
    public Instant workflowStartedAt() {
        return workflowStartedAt;
    }
 
    /** When the workflow reached a terminal state, or {@code null} if still running. */
    public Instant workflowFinishedAt() {
        return workflowFinishedAt;
    }
 
    /**
     * Returns an immutable point-in-time snapshot of this timeline
     * safe to pass to persistence or replay without holding a lock.
     * The returned {@link ExecutionRecord} will not reflect any events
     * that arrive after this call.
     */
    public ExecutionRecord snapshot() {
        return new ExecutionRecord(
                executionId,
                workflowStatus,
                workflowStartedAt,
                workflowFinishedAt,
                List.copyOf(events),
                Map.copyOf(nodeExecutions())
        );
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