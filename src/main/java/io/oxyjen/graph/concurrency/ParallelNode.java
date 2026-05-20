package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.result.Cancelled;
import io.oxyjen.execution.result.Failure;
import io.oxyjen.execution.result.NotExecuted;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;

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
		private final Map<String, TaskResult<O>> results;
        private final Map<String, O> successfulOutputs;
        private final Map<String, Throwable> failures;
        
        private final List<String> completionOrder;

        private final int successCount;
        private final int failureCount;
        private final int cancelledCount;
        private final int notExecutedCount;

        ParallelResult(
        		Map<String, TaskResult<O>> results,
                List<String> completionOrder
        ) {
            this.results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
            Map<String, O> outputs = new LinkedHashMap<>();
            Map<String, Throwable> errors = new LinkedHashMap<>();
            int successes = 0;
            int failures = 0;
            int cancelled = 0;
            int notExecuted = 0;
            for (Map.Entry<String, TaskResult<O>> entry : results.entrySet()) {
                String name = entry.getKey();
                TaskResult<O> result = entry.getValue();
                if (result instanceof Success<O> s) {
                    outputs.put(name, s.value());
                    successes++;

                } else if (result instanceof Failure<O> f) {
                    errors.put(name, f.error());
                    failures++;

                } else if (result instanceof Cancelled<O>) {
                    cancelled++;

                } else if (result instanceof NotExecuted<O>) {
                    notExecuted++;
                }
            }

            this.successfulOutputs = Collections.unmodifiableMap(outputs);
            this.failures = Collections.unmodifiableMap(errors);
            this.successCount = successes;
            this.failureCount = failures;
            this.cancelledCount = cancelled;
            this.notExecutedCount = notExecuted;
            this.completionOrder = Collections.unmodifiableList(new ArrayList<>(completionOrder));
        }

        public TaskResult<O> get(String name) { return results.get(name); }
        public O getOrDefault(String name, O def) { 
        	TaskResult<O> result = results.get(name);
        	if (result instanceof Success<O> s) {
        		return s.value();
        	}
        	return def;
        }
        public boolean succeeded(String name) { return results.get(name) instanceof Success<?>; }
        public boolean failed(String name) { return results.get(name) instanceof Failure<?>; }
        public boolean cancelled(String name) { return results.get(name) instanceof Cancelled<?>; }
        public boolean notExecuted(String name) { return results.get(name) instanceof NotExecuted<?>; }
        public Throwable getError(String name) { 
        	TaskResult<O> result = results.get(name);
        	if (result instanceof Failure<O> f) {
        		return f.error();
        	}
        	return null; 
        }
        public Map<String, TaskResult<O>> allResults() { return results; }
        public Map<String, O> allOutputs() { return successfulOutputs; }
        public Map<String, Throwable> allErrors() { return failures; }
        public boolean hasErrors() { return failureCount > 0; }
        public int successCount() { return successCount; }
        public int failureCount() { return failureCount; }
        public int cancelledCount() { return cancelledCount; }
        public int notExecutedCount() { return notExecutedCount; }
        public List<String> completionOrder() { return completionOrder; }
        public boolean hasIncomplete() { return cancelledCount > 0 || notExecutedCount > 0; }
        @Override
        public String toString() {
            return "ParallelResult{success="
                + successCount
                + ", failed="
                + failureCount
                + ", cancelled="
                + cancelledCount
                + ", notExecuted="
                + notExecutedCount
                + "}";
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
        Map<String, TaskResult<O>> results = new ConcurrentHashMap<>();
        List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());
        Map<String, CompletableFuture<TaskResult<O>>> futures = new LinkedHashMap<>();
        for (Task<I, O> task : tasks) {
            CompletableFuture<TaskResult<O>> future = CompletableFuture.supplyAsync(() -> {
                boolean acquired = false;
                try {
                    limiter.acquire();
                    acquired = true;
                    O value = task.fn().apply(input);
                    return new Success<>(value);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Cancelled<>("Interrupted");
                } catch (Exception e) {
                	return new Failure<>(e);
                } finally {
                    if (acquired) limiter.release();
                }
            }, executor);
            futures.put(task.name(), future);
        }
        List<CompletableFuture<Void>> collectors = new ArrayList<>();
        for (var entry : futures.entrySet()) {
            String taskName = entry.getKey();
            CompletableFuture<TaskResult<O>> future = entry.getValue();
            collectors.add(
                future.thenAccept(result -> {
                    results.put(taskName, result);
                    completionOrder.add(taskName);
                    if (failureMode == ExecutionRuntime.FailureMode.FAIL_FAST && result instanceof Failure<?>) {
                    	cancelAll(futures);
                    	throw new CompletionException(((Failure<O>) result).error());
                    } 
                })
            );
        }
        CompletableFuture<Void> all =
                CompletableFuture.allOf(collectors.toArray(new CompletableFuture[0]))
                        .orTimeout(timeout, TimeUnit.MILLISECONDS);
        try {
            all.join();
        } catch (CompletionException | CancellationException e) {
            cancelAll(futures);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
            	futures.forEach((name, future) -> {
            		results.putIfAbsent(name, new Cancelled<>("Timed out"));
            	});
            } else if (failureMode == ExecutionRuntime.FailureMode.FAIL_FAST) {
                throw new RuntimeException("[ParallelNode:" + name + "] failed", cause);
            }
        }
        ParallelResult<O> result = new ParallelResult<>(results, completionOrder);
        context.getLogger().info(
        	    "[ParallelNode:" + name + "] Done. success="
        	        + result.successCount()
        	        + " failures="
        	        + result.failureCount()
        	        + " cancelled="
        	        + result.cancelledCount()
       );
        return result;
    }

    private ParallelResult<O> runSequential(I input, NodeContext context) {
    	Map<String, TaskResult<O>> results = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
 
        for (Task<I, O> task : tasks) {
            try {
                results.put(task.name(), new Success<>(task.fn().apply(input)));
            } catch (Exception e) {
                results.put(task.name(), new Failure<>(e));
            }
            order.add(task.name());
        }
        return new ParallelResult<>(results, order);
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