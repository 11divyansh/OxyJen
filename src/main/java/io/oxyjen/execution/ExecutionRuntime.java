package io.oxyjen.execution;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Centralized concurrency runtime injected into {@link io.oxyjen.NodeContext}
 * by the {@link io.oxyjen.graph.ParallelExecutor} before graph execution begins.
 *
 * This is the single source of truth for:
 * - Which thread pool runs async work
 * - How many tasks can run simultaneously (Semaphore)
 * - What happens when a task fails (FailureMode)
 *
 * All nodes that need to submit async work - {@link io.oxyjen.graph.concurrency.ParallelNode}, - read from this runtime
 * instead of managing their own pools and limiters.
 *
 * This eliminates:
 * - Thread oversubscription (outer pool + inner pool stacking)
 * - Double limiting (executor semaphore + node semaphore fighting)
 * - Inconsistent failure behavior across nodes
 * - No global backpressure
 */
public final class ExecutionRuntime {
 
    public enum FailureMode {
        /**
         * On first task failure, cancel all in-flight tasks and propagate the exception.
         * Use when partial results are useless (e.g. all-or-nothing validation pipeline).
         */
        FAIL_FAST,
 
        /**
         * Isolate failing tasks, let the rest complete, surface all errors in the result.
         * Use when partial results are still valuable (e.g. batch LLM enrichment).
         */
        COLLECT_ERRORS
    }
 
    private final ExecutorService executor;
    private final Semaphore limiter;
    private final FailureMode failureMode;
    private final long defaultTimeoutMs;
 
    private ExecutionRuntime(
            ExecutorService executor,
            Semaphore limiter,
            FailureMode failureMode,
            long defaultTimeoutMs
    ) {
        this.executor = executor;
        this.limiter = limiter;
        this.failureMode = failureMode;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }
 
    public ExecutorService getExecutor() {
        return executor;
    }
 
    public Semaphore getLimiter() {
        return limiter;
    }
 
    public FailureMode getFailureMode() {
        return failureMode;
    }
 
    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }
 
    public static Builder builder() {
        return new Builder();
    }
 
    /**
     * Creates a sensible default runtime using available processors.
     * Used by ParallelExecutor when no custom runtime is configured.
     */
    public static ExecutionRuntime defaultRuntime() {
        int cores = Runtime.getRuntime().availableProcessors();
        return builder()
            .executor(Executors.newFixedThreadPool(cores))
            .maxConcurrency(cores)
            .failureMode(FailureMode.FAIL_FAST)
            .defaultTimeout(30, TimeUnit.SECONDS)
            .build();
    }
 
    public static final class Builder {
 
        private ExecutorService executor;
        private int maxConcurrency = Runtime.getRuntime().availableProcessors();
        private FailureMode failureMode = FailureMode.FAIL_FAST;
        private long defaultTimeoutMs = 30_000L;
 
        /**
         * Thread pool for all async node execution in the graph.
         * Default: fixed thread pool sized to available processors.
         */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }
 
        /**
         * Global concurrency cap — max tasks running simultaneously across
         * ALL nodes in the graph. Enforced via a shared Semaphore.
         * Default: number of available processors.
         */
        public Builder maxConcurrency(int max) {
            if (max < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
            this.maxConcurrency = max;
            return this;
        }
 
        /**
         * How the runtime handles task failures globally.
         * Individual nodes can override this if needed, but the runtime
         * default applies everywhere that doesn't override.
         */
        public Builder failureMode(FailureMode mode) {
            this.failureMode = mode;
            return this;
        }
 
        /**
         * Default timeout for async operations that don't specify their own.
         */
        public Builder defaultTimeout(long duration, TimeUnit unit) {
            this.defaultTimeoutMs = unit.toMillis(duration);
            return this;
        }
 
        public ExecutionRuntime build() {
            if (executor == null) {
                executor = Executors.newFixedThreadPool(maxConcurrency);
            }
            return new ExecutionRuntime(
                executor,
                new Semaphore(maxConcurrency),
                failureMode,
                defaultTimeoutMs
            );
        }
    }
}