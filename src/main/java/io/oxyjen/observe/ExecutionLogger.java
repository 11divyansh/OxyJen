package io.oxyjen.observe;

import java.util.logging.Logger;

import io.oxyjen.execution.ExecutionEvent;

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
}