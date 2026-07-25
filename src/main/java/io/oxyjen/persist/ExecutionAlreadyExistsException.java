package io.oxyjen.persist;

/**
 * Thrown by {@link ExecutionStore#save} when a record with the same
 * {@code executionId} already exists.
 *
 * <p>{@link io.oxyjen.execution.ExecutionRecord}s are immutable snapshots,
 * an execution should never be overwritten after it is persisted. If state
 * needs to change, a new snapshot with a new {@code executionId} should be
 * saved instead.
 */
public class ExecutionAlreadyExistsException extends ExecutionStoreException {
	
	private final String executionId;
	 
    public ExecutionAlreadyExistsException(String executionId) {
        super("Execution already exists: " + executionId);
        this.executionId = executionId;
    }
 
    /** The execution identifier that caused the conflict. */
    public String executionId() {
        return executionId;
    }
}