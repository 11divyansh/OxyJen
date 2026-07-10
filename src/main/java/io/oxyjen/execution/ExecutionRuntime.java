package io.oxyjen.execution;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.oxyjen.observe.ObservationBus;

/**
 * Centralized concurrency runtime injected into {@link io.oxyjen.NodeContext}
 * by the {@link io.oxyjen.graph.ParallelExecutor} before graph execution begins.
 *
 * This is the single source of truth for:
 * - Which thread pool runs async work
 * - How many tasks can run simultaneously (Semaphore)
 * - What happens when a task fails (FailureMode)
 *
 * All nodes that need to submit async work - {@link io.oxyjen.graph.concurrency.ParallelNode},
 * {@link io.oxyjen.graph.concurrency.MapNode}, etc. — read from this runtime
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
        COLLECT_ERRORS,
        
        /**
         * Continue execution, don't run downstream
         */
        SKIP_FAILED
    }
 
    private final ExecutorService executor;
    private final Semaphore limiter;
    private final FailureMode failureMode;
    private final long defaultTimeoutMs;
    
    /**
     * @deprecated Use {@link #observationBus()} and register a
     * {@link io.oxyjen.observe.ObservationListener} instead.
     */
    @Deprecated
    private final MetricsRegistry metrics;
    private final int maxConcurrency;
    
    private final ObservationBus observationBus;
 
    /**
     * Central observation bus for this runtime.
     * All execution events are emitted through this bus.
     */
    private ExecutionRuntime(
            ExecutorService executor,
            Semaphore limiter,
            FailureMode failureMode,
            long defaultTimeoutMs,
            int maxConcurrency,
            ObservationBus observationBus
    ) {
        this.executor = executor;
        this.limiter = limiter;
        this.failureMode = failureMode;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.metrics = new MetricsRegistry();
        this.maxConcurrency = maxConcurrency;
        this.observationBus = observationBus;
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
    
    /**
     * Returns the {@link ObservationBus} for this execution.
     * Register {@link io.oxyjen.observe.ObservationListener}s here to receive
     * all execution events (NodeStarted, NodeCompleted, BranchTaken, etc.).
     */
    public ObservationBus observationBus() {
        return observationBus;
    }
    
    /** @deprecated Use {@link #observationBus()} instead. */
    public MetricsRegistry getMetrics() {
        return metrics;
    }
    
    public int getMaxConcurrency() {
        return maxConcurrency;
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
        private ObservationBus observationBus = new ObservationBus();
 
        /**
         * Thread pool for all async node execution in the graph.
         * Default: fixed thread pool sized to available processors.
         */
        public Builder executor(ExecutorService executor) {
            this.executor = executor;
            return this;
        }
 
        /**
         * Global concurrency cap, max tasks running simultaneously across
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
        
        /**
         * Provide a pre-configured {@link ObservationBus} with listeners
         * already registered. If not set, a fresh empty bus is created
         * listeners can still be added later via
         * {@link ExecutionRuntime#observationBus()}.
         */
        public Builder observationBus(ObservationBus bus) {
            this.observationBus = bus;
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
                defaultTimeoutMs,
                maxConcurrency,
                observationBus
            );
        }
    }
    
    /**
     * @deprecated
     */
    public final class MetricsRegistry {
        private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
        public void increment(String key) {
            counters
                .computeIfAbsent(key, k -> new AtomicLong())
                .incrementAndGet();
        }
        public long get(String key) {
            AtomicLong counter = counters.get(key);
            return counter == null ? 0 : counter.get();
        }
    }
}