package io.oxyjen.persist;

import java.util.Optional;

import io.oxyjen.execution.ExecutionRecord;

public interface ExecutionStore {
	
	/**
     * Persists an {@link ExecutionRecord}.
     *
     * @param record  the snapshot to persist; must not be {@code null}
     * @throws ExecutionStoreException       if the write fails
     * @throws ExecutionAlreadyExistsException if a record with the same
     *         {@code executionId} already exists, executions are immutable
     */
	void save(ExecutionRecord record);
	
	/**
     * Loads a previously-saved record by its execution identifier.
     *
     * @param executionId  the identifier to look up
     * @return the record, or {@link Optional#empty()} if not found
     * @throws ExecutionStoreException if the read fails
     */
	Optional<ExecutionRecord> load(String executionId);
	
	/**
     * Returns {@code true} if a record with the given execution identifier
     * has been saved. Cheaper than {@link #load} when you only need to
     * check existence.
     *
     * @param executionId  the identifier to check
     * @throws ExecutionStoreException if the check fails
     */
	boolean exists(String executionId);
	
	/**
     * Deletes a previously-saved record. No-op if the record does not exist.
     * Useful for retention policies and test cleanup.
     *
     * @param executionId  the identifier to delete
     * @throws ExecutionStoreException if the delete fails
     */
	void delete(String executionId);
}