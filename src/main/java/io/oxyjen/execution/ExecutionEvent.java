package io.oxyjen.execution;

import java.time.Instant;
import java.util.Map;

import io.oxyjen.execution.metrics.NodeMetrics;

/**
 * Core event types emitted during workflow execution.
 *
 * <p>Every observable thing that happens during a workflow run — a node starting,
 * a branch being taken, a checkpoint being saved, a chunk being streamed — is
 * represented as one of these events. Nothing else in {@code io.oxyjen.observe},
 * {@code io.oxyjen.persist}, {@code io.oxyjen.replay}, or {@code io.oxyjen.stream}
 * tracks state independently; they all derive their view from a sequence of
 * {@link ExecutionEvent}s.
 *
 * <p>Because OxyJen workflows are deterministic (the graph controls execution,
 * not the LLM), the same workflow run with the same input produces the same
 * event sequence every time. That property is what makes {@code replay} and
 * {@code diff} meaningful, they are not reconstructing emergent agent behavior,
 * they are replaying a deterministic record.
 *
 * <p>Every event carries {@code executionId} (the workflow run) and {@code at}
 * (when it happened). Node-scoped events additionally carry {@code nodeId}.
 *
 * <p><b>Correlation:</b> Correlation identifiers beyond {@code executionId}
 * are intentionally omitted until OxyJen supports distributed or cross-process
 * execution. Fields such as {@code traceId}, {@code spanId}, and
 * {@code parentSpanId} can be added later without affecting existing consumers.
 */
public sealed interface ExecutionEvent {

    /** The workflow execution this event belongs to. */
    String executionId();

    /** When this event occurred. */
    Instant at();

    /**
     * Marker sub-interface for all events scoped to a specific node.
     *
     * <p>Lets exporters, listeners, and timeline builders handle any
     * node-related event with a single pattern match rather than listing
     * every concrete type:
     *
     * <pre>{@code
     * if (event instanceof NodeEvent nodeEvent) {
     *     process(nodeEvent.nodeId());
     * }
     * }</pre>
     */
    public sealed interface NodeEvent extends ExecutionEvent permits
        NodeStarted,
        NodeCompleted,
        NodeFailed,
        RetryAttempt,
        NodeSkipped,
        BranchTaken,
        ParallelStarted,
        ParallelCompleted,
        CheckpointCreated,
        CheckpointRestored,
        ChunkGenerated {
/** Identifier of the node this event is scoped to. */
    	String nodeId();
    }
    /**
     * Emitted once, when a workflow execution begins.
     *
     * @param workflowId  identifier of the workflow definition being run
     * @param input       the initial input bound into the execution context
     */
    record WorkflowStarted(
            String executionId,
            Instant at,
            String workflowId,
            // TODO v1: replace with ExecutionContextSnapshot once replay/checkpoint
            // serialization is introduced
            Map<String, Object> input
    ) implements ExecutionEvent {}

    /**
     * Emitted once, when a workflow execution reaches a terminal completed
     * or failed state. For suspension see {@link ExecutionSuspended}; for
     * explicit cancellation see {@link ExecutionCancelled} — neither of those
     * also emits this event, since they are themselves terminal-for-now states.
     *
     * @param status      terminal status of the execution: {@code COMPLETED} or {@code FAILED}
     * @param durationMs  total wall-clock time from {@link WorkflowStarted} to this event
     */
    record WorkflowFinished(
            String executionId,
            Instant at,
            ExecutionStatus status,
            long durationMs
    ) implements ExecutionEvent {
    	public WorkflowFinished {
            if (status != ExecutionStatus.COMPLETED && status != ExecutionStatus.FAILED) {
                throw new IllegalArgumentException(
                        "WorkflowFinished requires COMPLETED or FAILED status, got: " + status);
            }
        }
    }

    /**
     * Emitted when a workflow execution is explicitly cancelled before reaching
     * a terminal node, distinct from {@link #WorkflowFinished} because the
     * cause matters for replay and operability (manual cancel vs. timeout vs.
     * shutdown vs. a parent execution cancelling its children).
     *
     * @param reason  why the execution was cancelled
     */
    record ExecutionCancelled(
            String executionId,
            Instant at,
            CancellationReason reason
    ) implements ExecutionEvent {}

    /** Why an execution was cancelled. */
    enum CancellationReason {
        /** A caller explicitly requested cancellation. */
        MANUAL,
        /** The execution exceeded a configured timeout. */
        TIMEOUT,
        /** The host process is shutting down. */
        SHUTDOWN,
        /** A parent execution (or parallel region) was cancelled, taking this one with it. */
        PARENT_CANCELLED
    }

    /**
     * Emitted when execution pauses awaiting external input — distinct from
     * {@link CheckpointCreated}, since a checkpoint can be taken without the
     * workflow actually stopping (e.g. periodic checkpointing of a long-running
     * graph). This event specifically marks the workflow as not progressing
     * until {@link ExecutionResumed} is observed.
     *
     * @param nodeId  the node at which execution suspended (typically a human-approval node)
     */
    record ExecutionSuspended(
            String executionId,
            Instant at,
            String nodeId
    ) implements ExecutionEvent {}

    /**
     * Emitted when a previously-suspended execution resumes and continues
     * past the suspension point.
     *
     * @param nodeId  the node execution is resuming from
     */
    record ExecutionResumed(
            String executionId,
            Instant at,
            String nodeId
    ) implements ExecutionEvent {}

    /**
     * Emitted when a node begins execution.
     *
     * @param nodeId    identifier of the node within the workflow graph
     * @param attempt   1-indexed attempt number (incremented on retry)
     */
    record NodeStarted(
            String executionId,
            Instant at,
            String nodeId,
            int attempt
    ) implements NodeEvent {}

    /**
     * Emitted when a node completes successfully.
     *
     * @param nodeId   identifier of the node within the workflow graph
     * @param metrics  typed metrics for this attempt — duration lives here
     *                 ({@link NodeMetrics#duration()}) rather than as a
     *                 separate field, since every metrics variant carries it
     */
    record NodeCompleted(
            String executionId,
            Instant at,
            String nodeId,
            NodeMetrics metrics
    ) implements NodeEvent {}

    /**
     * Emitted when a node fails. If the node is configured to retry, this is
     * typically followed by a {@link RetryAttempt}; otherwise it propagates
     * to a {@link WorkflowFinished} with a failed status.
     *
     * @param nodeId   identifier of the node within the workflow graph
     * @param failure  structured failure details (type, message, stack trace)
     * @param attempt  which attempt number failed
     */
    record NodeFailed(
            String executionId,
            Instant at,
            String nodeId,
            FailureInfo failure,
            int attempt
    ) implements NodeEvent {}

    /**
     * Emitted when a failed node is about to be retried.
     *
     * @param nodeId         identifier of the node within the workflow graph
     * @param attempt        the attempt number about to be made (i.e. previous + 1)
     * @param failure        structured details of the failure that triggered this retry
     * @param backoffMillis  delay before this attempt is made, if any backoff policy applies
     */
    record RetryAttempt(
            String executionId,
            Instant at,
            String nodeId,
            int attempt,
            FailureInfo failure,
            long backoffMillis
    ) implements NodeEvent {}

    /**
     * Emitted when a node is skipped entirely rather than executed — e.g. a
     * branch not taken, or a conditional node whose guard evaluated false.
     *
     * <p>Without this event, a skipped node is indistinguishable from a crash,
     * a cancellation, or a logging gap: there's simply no record at all. This
     * event makes "intentionally not run" an explicit, queryable fact, which
     * matters for both replay and diff (a node that was skipped in run #21
     * but executed in run #22 is a meaningful divergence to surface).
     *
     * @param nodeId  identifier of the node that was skipped
     * @param reason  human-readable reason the node was skipped (e.g. the branch condition)
     */
    record NodeSkipped(
            String executionId,
            Instant at,
            String nodeId,
            String reason
    ) implements NodeEvent {}

    /**
     * Emitted when a {@code BranchNode} resolves which route to take.
     *
     * @param nodeId    the branch node that made the decision
     * @param routeKey  identifier of the chosen route/edge
     */
    record BranchTaken(
            String executionId,
            Instant at,
            String nodeId,
            String routeKey
    ) implements NodeEvent {}
    
    /**
    * Emitted when a parallel region of the graph (ParallelNode, MapNode,
    * GatherNode fanout, ForEachNode, etc.) begins.
    *
    * @param nodeId     the node initiating the parallel region
    * @param taskCount  number of concurrent tasks being started — named
    *                   generically rather than "branchCount" since not every
    *                   parallel node type creates graph branches (e.g. MapNode
    *                   and ForEachNode fan out over data, not edges)
    */
   record ParallelStarted(
           String executionId,
           Instant at,
           String nodeId,
           int taskCount
   ) implements NodeEvent {}

   /**
    * Emitted when a parallel region completes, all tasks have either
    * finished or been aggregated (e.g. by GatherNode).
    *
    * @param nodeId      the node that initiated the parallel region
    * @param succeeded   number of tasks that completed successfully
    * @param failed      number of tasks that failed
    * @param durationMs  wall-clock duration of the parallel region
    */
   record ParallelCompleted(
           String executionId,
           Instant at,
           String nodeId,
           int succeeded,
           int failed,
           long durationMs
   ) implements NodeEvent {}

   /**
    * Emitted when execution state is durably checkpointed. This does
    * <b>not</b> imply execution paused, periodic checkpointing of a
    * long-running workflow can happen while it keeps progressing. For an
    * actual pause, see {@link ExecutionSuspended}.
    *
    * @param nodeId  the node at which the checkpoint was taken
    */
   record CheckpointCreated(
           String executionId,
           Instant at,
           String nodeId
   ) implements NodeEvent {}

   /**
    * Emitted when a previously-saved checkpoint is loaded back into a live
    * execution — typically as part of {@code replay.fromCheckpoint()} or
    * when resuming a suspended workflow.
    *
    * @param nodeId  the node the checkpoint was restored at
    */
   record CheckpointRestored(
           String executionId,
           Instant at,
           String nodeId
   ) implements NodeEvent {}

   /**
    * Emitted for each chunk produced by a streaming LLM node. High-frequency
    * compared to other event types, exporters and stores that don't care
    * about chunk-level detail should filter these out rather than persist
    * every one.
    *
    * <p>Named {@code chunk} rather than {@code token} deliberately: providers
    * don't stream at consistent granularity. OpenAI may stream sub-word
    * fragments, Claude tends to stream larger pieces, Gemini may stream whole
    * sentences, and some providers stream structured JSON fragments rather
    * than text at all. "Chunk" makes no claim about granularity.
    *
    * @param nodeId  the streaming node producing chunks
    * @param chunk   the text (or structured-fragment) chunk
    * @param index   0-indexed position of this chunk within the stream
    */
   record ChunkGenerated(
           String executionId,
           Instant at,
           String nodeId,
           String chunk,
           int index
   ) implements NodeEvent {}
}