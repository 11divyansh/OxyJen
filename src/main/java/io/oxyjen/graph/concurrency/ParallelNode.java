package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;

/**
* Executes a fixed set of {@link NodePlugin}s concurrently, all receiving the same input.
*
* ParallelNode is a self-contained fan-out + fan-in: it submits all registered tasks
* to a thread pool, waits for all to complete (or timeout), and returns a
* {@link ParallelResult} containing each task's output keyed by node name.
*
* This is different from RouterNode + MergeNode:
* - RouterNode/MergeNode: fan-out lives in the graph topology (separate nodes, separate edges)
* - ParallelNode: fan-out is encapsulated inside a single node - simpler for fixed task sets
*
* Use ParallelNode when:
* - You have a fixed, known set of tasks to run on one input
* - You want the parallelism encapsulated (not visible in the graph topology)
* - Tasks are independent and don't need to communicate
*/
public class ParallelNode<I,O> implements NodePlugin<I, ParallelNode.ParallelResult<O>> {
	
	public static final class ParallelResult<O> {
        private final Map<String, O> outputs;
        private final Map<String, Throwable> errors;
        private final List<String> completionOrder;

        ParallelResult(
                Map<String, O> outputs,
                Map<String, Throwable> errors,
                List<String> completionOrder
        ) {
            this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
            this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
            this.completionOrder = Collections.unmodifiableList(new ArrayList<>(completionOrder));
        }

        public O get(String name) { return outputs.get(name); }
        public O getOrDefault(String name, O def) { return outputs.getOrDefault(name, def); }
        public boolean succeeded(String name) { return outputs.containsKey(name); }
        public boolean failed(String name) { return errors.containsKey(name); }
        public Throwable getError(String name) { return errors.get(name); }
        public Map<String, O> allOutputs() { return outputs; }
        public Map<String, Throwable> allErrors() { return errors; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public List<String> completionOrder() { return completionOrder; }
        public int successCount() { return outputs.size(); }
        public int errorCount() { return errors.size(); }
        @Override
        public String toString() {
            return "ParallelResult{succeeded=" + outputs.keySet()
                + ", failed=" + errors.keySet() + "}";
        }
    }

    private record Task<I, O>(String name, Function<I, O> fn) {}

    private final String name;
    private final List<Task<I, O>> tasks;
    private final long timeoutMs;

    private ParallelNode(
            String name,
            List<Task<I, O>> tasks,
            long timeoutMs
    ) {
        this.name = name;
        this.tasks = List.copyOf(tasks);
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ParallelResult<O> process(I input, NodeContext context) {
    	ExecutionRuntime runtime = context.getRuntime();
        if (runtime == null) {
            context.getLogger().warning(
                "[ParallelNode:" + name + "] No ExecutionRuntime in context. " +
                "Running tasks sequentially. Use ParallelExecutor for concurrent execution."
            );
            return runSequential(input, context);
        }
        ExecutorService executor = runtime.getExecutor();
        Semaphore limiter = runtime.getLimiter();
        ExecutionRuntime.FailureMode failureMode = runtime.getFailureMode();
        long timeout = timeoutMs > 0 ? timeoutMs : runtime.getDefaultTimeoutMs();
        context.getLogger().info("[ParallelNode:" + name + "] Starting " + tasks.size() + " tasks");
        Map<String, O> outputs = new ConcurrentHashMap<>();
        Map<String, Throwable> errors = new ConcurrentHashMap<>();
        List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());
        Map<String, CompletableFuture<O>> futures = new LinkedHashMap<>();
        for (Task<I, O> task : tasks) {
            CompletableFuture<O> future = CompletableFuture.supplyAsync(() -> {
                boolean acquired = false;
                try {
                    limiter.acquire();
                    acquired = true;
                    return task.fn().apply(input);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    if (acquired) limiter.release();
                }
            }, executor);
            futures.put(task.name(), future);
        }
        List<CompletableFuture<Void>> collectors = new ArrayList<>();
        for (var entry : futures.entrySet()) {
            String taskName = entry.getKey();
            CompletableFuture<O> future = entry.getValue();
            collectors.add(
                future.handle((result, ex) -> {
                    if (ex != null) {
                        Throwable cause = (ex instanceof CompletionException)
                                ? ex.getCause()
                                : ex;
                        if (failureMode == ExecutionRuntime.FailureMode.FAIL_FAST) {
                            cancelAll(futures);
                            throw new CompletionException(cause);
                        } else {
                            errors.put(taskName, cause);
                        }
                    } else {
                        outputs.put(taskName, result);
                    }
                    completionOrder.add(taskName);
                    return null;
                })
            );
        }
        CompletableFuture<Void> all =
                CompletableFuture.allOf(collectors.toArray(new CompletableFuture[0]))
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        try {
            all.join();
        } catch (CompletionException e) {
            cancelAll(futures);
            if (failureMode == ExecutionRuntime.FailureMode.FAIL_FAST) {
                throw new RuntimeException("[ParallelNode:" + name + "] failed", e.getCause());
            }
        }
        context.getLogger().info(
                "[ParallelNode:" + name + "] Done. success=" + outputs.size() +
                " errors=" + errors.size()
        );
        return new ParallelResult<>(outputs, errors, completionOrder);
    }

    private ParallelResult<O> runSequential(I input, NodeContext context) {
        Map<String, O> outputs = new LinkedHashMap<>();
        Map<String, Throwable> errors = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
 
        for (Task<I, O> task : tasks) {
            try {
                outputs.put(task.name(), task.fn().apply(input));
            } catch (Exception e) {
                errors.put(task.name(), e);
            }
            order.add(task.name());
        }
        return new ParallelResult<>(outputs, errors, order);
    }
    private void cancelAll(Map<String, ? extends CompletableFuture<?>> futures) {
        futures.values().forEach(f -> f.cancel(true));
    }

    @Override
    public String getName() {
        return name;
    }
    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    public static class Builder<I, O> {
        private final List<Task<I, O>> tasks = new ArrayList<>();
        private long timeoutMs = 0;

        public Builder<I, O> task(String name, Function<I, O> fn) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(fn);
            tasks.add(new Task<>(name, fn));
            return this;
        }

        public Builder<I, O> timeout(long duration, TimeUnit unit) {
            this.timeoutMs = unit.toMillis(duration);
            return this;
        }

        public ParallelNode<I, O> build(String name) {
            if (tasks.isEmpty()) {
                throw new IllegalStateException("ParallelNode must have at least one task");
            }
            Set<String> names = new HashSet<>();
            for (Task<I, O> t : tasks) {
                if (!names.add(t.name())) {
                    throw new IllegalStateException("Duplicate task name: " + t.name());
                }
            }
            return new ParallelNode<>(
                    name,
                    tasks,
                    timeoutMs
            );
        }
    }
}