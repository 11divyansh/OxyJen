package io.oxyjen.execution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.oxyjen.execution.metrics.NodeMetrics;

/**
 * A normalized, queryable record of a single node's execution within a workflow run.
 *
 * <p>Raw {@link ExecutionEvent}s ({@code NodeStarted}, {@code NodeCompleted},
 * {@code NodeFailed}, {@code RetryAttempt}, {@code NodeSkipped}, ...) are the
 * source of truth, but they're awkward to query directly, you'd have to scan
 * and correlate events by {@code nodeId} and {@code attempt} every time.
 * {@code NodeExecution} is the folded, denormalized view: one instance per
 * node per execution, built by an {@link ExecutionTimeline} as events arrive.
 *
 * <p>This is the type {@code io.oxyjen.observe.MetricsCollector},
 * {@code CostCalculator}, and {@code LatencyCollector} read from, they don't
 * track cost or latency independently, they derive it from
 * {@link NodeMetrics#duration()} (and the LLM/tool/http/db-specific fields)
 * already captured in {@link #metrics()}.
 *
 * @param executionId   the workflow execution this node run belongs to
 * @param nodeId        identifier of the node within the workflow graph
 * @param startedAt     when the (final, successful or terminally-failed) attempt began;
 *                      empty if the node was {@link NodeStatus#SKIPPED skipped} and
 *                      never actually started
 * @param finishedAt    when the node reached a terminal state, empty if still running
 * @param status        terminal status of this node's execution
 * @param attempts      total number of attempts made (1 if no retries occurred, 0 if skipped)
 * @param metrics       typed metrics captured for the final attempt; empty for a
 *                      {@link NodeStatus#SKIPPED skipped} node, or a node still
 *                      {@link NodeStatus#RUNNING running} duration of the final
 *                      attempt lives inside this via {@link NodeMetrics#duration()}
 *                      rather than as a separate field here, avoiding the same
 *                      duration being tracked in two places
 * @param failures      details of any failed attempts that preceded success/terminal failure,
 *                      in chronological order; empty if the node succeeded on the first attempt
 *                      or was skipped
 */
public record NodeExecution(
        String executionId,
        String nodeId,
        Optional<Instant> startedAt,
        Optional<Instant> finishedAt,
        NodeStatus status,
        int attempts,
        Optional<NodeMetrics> metrics,
        List<AttemptFailure> failures
) {
	
	public NodeExecution {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be >= 0");
        }

        failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
    }

    /**
     * Details of a single failed attempt for this node.
     *
     * @param attempt  which attempt number failed
     * @param failure  structured failure details (type, message, stack trace)
     * @param at       when the failure occurred
     */
    public record AttemptFailure(
            int attempt,
            FailureInfo failure,
            Instant at
    ) {}

    /** True if this node required more than one attempt to reach its terminal status. */
    public boolean wasRetried() {
        return attempts > 1;
    }

    /** True if this node was never executed because its branch/guard wasn't taken. */
    public boolean wasSkipped() {
        return status == NodeStatus.SKIPPED;
    }
    
    public boolean isRunning() {
        return status == NodeStatus.RUNNING;
    }

    public boolean isCompleted() {
        return status == NodeStatus.COMPLETED;
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }
}