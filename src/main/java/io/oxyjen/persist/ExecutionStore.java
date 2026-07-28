package io.oxyjen.persist;

import java.util.Optional;
import java.util.function.Consumer;

import io.oxyjen.execution.ExecutionRecord;

/**
* Persistence SPI for {@link ExecutionRecord}s.
*
* <p>Stores and retrieves immutable execution snapshots. The store knows
* nothing about execution — it simply persists and retrieves
* {@link ExecutionRecord}s. Any backend can implement this interface:
*
* <pre>
* InMemoryExecutionStore   — zero-dep, for tests and short-lived runs
* SqliteExecutionStore     — default embedded, zero infra required
* JdbcExecutionStore       — Postgres, MySQL, any JDBC source
* </pre>
*
* <p><b>Immutability contract:</b> executions are immutable snapshots.
* {@link #save} rejects duplicates — an execution should never change after
* it is persisted. Running executions belong in {@link io.oxyjen.execution.ExecutionTimeline};
* only call {@link #save} with a snapshot produced by
* {@link io.oxyjen.execution.ExecutionTimeline#snapshot()}.
* Nothing stops you from saving multiple RUNNING snapshots of the same
* logical execution (each gets its own {@code executionId}) for
* checkpointing purposes.
*
* <p><b>Synchronous by design:</b> all methods block until the operation
* completes. Use {@link AsyncExecutionStore} to wrap any implementation
* for non-blocking behaviour without complicating this interface.
*
* <p><b>Failure isolation:</b> a persistence failure must never fail a
* workflow. Callers should catch {@link ExecutionStoreException} and decide
* independently whether to retry, log, or alert.
*
* <p><b>Thread safety:</b> all implementations must be thread-safe.
* The executor may persist multiple workflow completions simultaneously.
*/
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
	
	/**
    * Queries records matching the given {@link ExecutionQuery}.
    * Results are paginated — never returns an unbounded list.
    *
    * <pre>{@code
    * ExecutionPage page = store.find(ExecutionQuery.builder()
    *         .workflowId("summarizer")
    *         .status(COMPLETED)
    *         .limit(50)
    *         .build());
    * }</pre>
    *
    * @param query  the query to apply; use {@link ExecutionQuery#all()} for
    *               unfiltered results up to the default limit
    * @throws ExecutionStoreException if the query fails
    */
   ExecutionPage find(ExecutionQuery query);

   /**
    * Lambda-style query variant — builds the query inline via a consumer
    * and delegates to {@link #find(ExecutionQuery)}.
    *
    * <pre>{@code
    * store.find(q -> q.status(FAILED).limit(20));
    * }</pre>
    */
   default ExecutionPage find(Consumer<ExecutionQuery.Builder> spec) {
       return find(ExecutionQuery.of(spec));
   }
}