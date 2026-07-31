package io.oxyjen.persist;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import io.oxyjen.execution.ExecutionRecord;

/**
 * Non-blocking wrapper around any synchronous {@link ExecutionStore}.
 *
 * <p>Every operation is submitted to an {@link Executor} and returns a
 * {@link CompletableFuture}. The underlying store is unchanged — this class
 * adds async semantics without each backend needing to implement them.
 *
 * <pre>{@code
 * ExecutionStore sqlite = new SqliteExecutionStore(path);
 * AsyncExecutionStore async = new AsyncExecutionStore(sqlite);
 *
 * async.save(record)
 *      .exceptionally(ex -> { log.error("persist failed", ex); return null; });
 * }</pre>
 *
 * <p>Uses {@link ForkJoinPool#commonPool()} by default. Supply a dedicated
 * {@link Executor} for production use to avoid starving other tasks on the
 * common pool.
 */
public final class AsyncExecutionStore {

    private final ExecutionStore delegate;
    private final Executor executor;

    public AsyncExecutionStore(ExecutionStore delegate) {
        this(delegate, ForkJoinPool.commonPool());
    }

    /**
     * Creates an asynchronous wrapper using the supplied executor.
     *
     * @param delegate synchronous store to wrap
     * @param executor executor used for asynchronous execution
     */
    public AsyncExecutionStore(ExecutionStore delegate, Executor executor) {
    	this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    	this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }
    
    /**
     * Convenience factory using the common ForkJoinPool.
     *
     * @param delegate synchronous store to wrap
     * @return asynchronous wrapper
     */
    public static AsyncExecutionStore of(ExecutionStore delegate) {
        return new AsyncExecutionStore(delegate);
    }

    /**
     * Asynchronously persists an execution record.
     */
    public CompletableFuture<Void> save(ExecutionRecord record) {
        return CompletableFuture.runAsync(() -> delegate.save(record), executor);
    }

    /**
     * Asynchronously loads an execution record.
     */
    public CompletableFuture<Optional<ExecutionRecord>> load(String executionId) {
        return CompletableFuture.supplyAsync(() -> delegate.load(executionId), executor);
    }

    /**
     * Asynchronously checks whether an execution exists.
     */
    public CompletableFuture<Boolean> exists(String executionId) {
        return CompletableFuture.supplyAsync(() -> delegate.exists(executionId), executor);
    }

    /**
     * Asynchronously deletes an execution record.
     */
    public CompletableFuture<Void> delete(String executionId) {
        return CompletableFuture.runAsync(() -> delegate.delete(executionId), executor);
    }

    /**
     * Asynchronously executes a query.
     */
    public CompletableFuture<ExecutionPage> find(ExecutionQuery query) {
        return CompletableFuture.supplyAsync(() -> delegate.find(query), executor);
    }
    
    /**
     * Lambda-style query variant matching
     * {@link ExecutionStore#find(java.util.function.Consumer)}.
     *
     * <pre>{@code
     * async.find(q -> q
     *         .status(COMPLETED)
     *         .limit(50))
     *      .thenAccept(page -> ...);
     * }</pre>
     */
    public CompletableFuture<ExecutionPage> find(Consumer<ExecutionQuery.Builder> spec) {
        return find(ExecutionQuery.of(spec));
    }

    /** Returns the underlying synchronous store. */
    public ExecutionStore delegate() {
        return delegate;
    }
}