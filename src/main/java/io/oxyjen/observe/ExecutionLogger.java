package io.oxyjen.observe;

import java.util.logging.Logger;

import io.oxyjen.execution.ExecutionEvent;
import io.oxyjen.execution.metrics.NodeMetrics;

public final class ExecutionLogger implements ObservationListener {
	private static final Logger LOG = Logger.getLogger("io.oxyjen");

	private static final String PREFIX = "[oxyjen] ";
	private static final int LABEL_WIDTH = 22;

    @Override
    public void onEvent(ExecutionEvent event) {

        if (event instanceof ExecutionEvent.WorkflowStarted e) {
            info(pad("WORKFLOW STARTED")
                    + " executionId=" + e.executionId()
                    + " workflowId=" + e.workflowId());
        } else if (event instanceof ExecutionEvent.WorkflowFinished e) {
            info(pad("WORKFLOW FINISHED")
                    + " executionId=" + e.executionId()
                    + " status=" + e.status()
                    + " duration=" + e.durationMs() + "ms");
        } else if (event instanceof ExecutionEvent.ExecutionCancelled e) {
            warn(pad("EXECUTION CANCELLED")
                    + " executionId=" + e.executionId()
                    + " reason=" + e.reason());
        } else if (event instanceof ExecutionEvent.ExecutionSuspended e) {
            info(pad("EXECUTION SUSPENDED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId());
        } else if (event instanceof ExecutionEvent.ExecutionResumed e) {
            info(pad("EXECUTION RESUMED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId());
        }
        
        if (event instanceof ExecutionEvent.NodeStarted e) {
            info(pad("NODE STARTED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " attempt=" + e.attempt());
        }
        else if (event instanceof ExecutionEvent.NodeCompleted e) {
            info(pad("NODE COMPLETED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + formatMetrics(e.metrics()));
        }
        else if (event instanceof ExecutionEvent.NodeFailed e) {
            StringBuilder msg = new StringBuilder();
            msg.append(pad("NODE FAILED"))
               .append(" executionId=").append(e.executionId())
               .append(" node=").append(e.nodeId())
               .append(" attempt=").append(e.attempt())
               .append(" error=").append(e.failure().exceptionType());
            if (e.failure().message() != null && !e.failure().message().isBlank()) {
                msg.append(" message=").append(e.failure().message());
            }
            warn(msg.toString());

        }
        else if (event instanceof ExecutionEvent.RetryAttempt e) {
            warn(pad("RETRY ATTEMPT")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " attempt=" + e.attempt()
                    + " backoff=" + e.backoffMillis() + "ms"
                    + " cause=" + e.failure().exceptionType());
        }
        else if (event instanceof ExecutionEvent.NodeSkipped e) {
            info(pad("NODE SKIPPED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " reason=" + e.reason());
        }
        
        else if (event instanceof ExecutionEvent.BranchTaken e) {
            info(pad("BRANCH TAKEN")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " route=" + e.routeKey());

        }
        else if (event instanceof ExecutionEvent.ParallelStarted e) {
            info(pad("PARALLEL STARTED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " tasks=" + e.taskCount());

        }
        else if (event instanceof ExecutionEvent.ParallelCompleted e) {
            info(pad("PARALLEL COMPLETED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " succeeded=" + e.succeeded()
                    + " failed=" + e.failed()
                    + " duration=" + e.durationMs() + "ms");

        }
        else if (event instanceof ExecutionEvent.CheckpointCreated e) {
            info(pad("CHECKPOINT CREATED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId());

        }
        else if (event instanceof ExecutionEvent.CheckpointRestored e) {
            info(pad("CHECKPOINT RESTORED")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId());

        }
        else if (event instanceof ExecutionEvent.ChunkGenerated e) {
            // Streaming generates many events, so keeping this at FINE level.
            LOG.fine(PREFIX
                    + pad("CHUNK")
                    + " executionId=" + e.executionId()
                    + " node=" + e.nodeId()
                    + " index=" + e.index());

        }
    }

    private void info(String message) {
        LOG.info(PREFIX + message);
    }

    private void warn(String message) {
        LOG.warning(PREFIX + message);
    }

    /**
     * Pads event labels so log values align vertically.
     */
    private String pad(String label) {
        return String.format("%-" + LABEL_WIDTH + "s", label);
    }
    
    /**
     * Formats node metrics into a concise log suffix.
     */
    private static String formatMetrics(NodeMetrics metrics) {

        if (metrics == null) {
            return "";
        }

        if (metrics instanceof NodeMetrics.LlmNodeMetrics m) {
            StringBuilder sb = new StringBuilder();
            sb.append(" duration=")
              .append(m.duration().toMillis())
              .append("ms");

            if (m.totalTokens() != null) {
                sb.append(" tokens=").append(m.totalTokens());
            }

            if (m.costMicros() > 0) {
                sb.append(" cost=$").append(m.costUsd().toPlainString());
            }

            if (m.modelInfo() != null) {
                sb.append(" provider=").append(m.modelInfo().provider());
                sb.append(" model=").append(m.modelInfo().model());
            }

            if (Boolean.FALSE.equals(m.outputValid())) {
                sb.append(" outputValid=false");
            }

            return sb.toString();
        }

        if (metrics instanceof NodeMetrics.GraphNodeMetrics m) {
            return " duration=" + m.duration().toMillis() + "ms";
        }
        return "";
    }
}