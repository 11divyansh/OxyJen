package io.oxyjen.persist;

import io.oxyjen.execution.ExecutionRecord;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

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

    public AsyncExecutionStore(ExecutionStore delegate, Executor executor) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (executor == null) throw new IllegalArgumentException("executor must not be null");
        this.delegate = delegate;
        this.executor = executor;
    }

    public CompletableFuture<Void> save(ExecutionRecord record) {
        return CompletableFuture.runAsync(() -> delegate.save(record), executor);
    }

    public CompletableFuture<Optional<ExecutionRecord>> load(String executionId) {
        return CompletableFuture.supplyAsync(() -> delegate.load(executionId), executor);
    }

    public CompletableFuture<Boolean> exists(String executionId) {
        return CompletableFuture.supplyAsync(() -> delegate.exists(executionId), executor);
    }

    public CompletableFuture<Void> delete(String executionId) {
        return CompletableFuture.runAsync(() -> delegate.delete(executionId), executor);
    }

    public CompletableFuture<ExecutionPage> find(ExecutionQuery query) {
        return CompletableFuture.supplyAsync(() -> delegate.find(query), executor);
    }

    /** Returns the underlying synchronous store. */
    public ExecutionStore delegate() {
        return delegate;
    }
}