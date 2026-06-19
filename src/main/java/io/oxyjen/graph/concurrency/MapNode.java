package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiFunction;
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
 * Applies a processing function to every element of an input collection concurrently.
 *
 * MapNode is the parallel equivalent of {@code list.stream().map(fn).toList()}.
 * It accepts any {@link Iterable} as input, submits one task per element to a
 * thread pool, and returns a {@link MapResult} preserving the original order.
 *
 * Unlike {@link ParallelNode} (fixed set of different tasks on one input),
 * MapNode runs the same function on many inputs simultaneously.
 *
 * Use MapNode when:
 * - Input is a collection of items to process independently
 * - Same logic applies to every element (batch LLM calls, document processing, etc.)
 * - You want order preserved in the output despite parallel execution
 *
 * Usage:
 * <pre>{@code
 *   MapNode<String, String> mapper = MapNode.<String, String>builder()
 *       .mapWith(text -> llm.summarize(text))
 *       .parallelism(8)
 *       .timeout(60, TimeUnit.SECONDS)
 *       .continueOnError()
 *       .build("batch-summarize");
 *
 *   // input: List<String> of documents
 *   // output: MapResult
 *   MapResult result = (MapResult) mapNode.process(documents, context);
 *   List<String> summaries = result.toList();
 *   List<String> failed    = result.failedIndices().stream()
 *       .map(i -> documents.get(i)).toList();
 * }</pre>
 *
 * @param <I> The element type of the input collection.
 * @param <O> The output type produced by the mapping function.
 */
public class MapNode<I, O> implements NodePlugin<Iterable<I>, MapNode.MapResult<O>> {
	 
	 private record IndexedResult<O>(int index, TaskResult<O> result) {
		 IndexedResult {
			 if (index < 0) throw new IllegalArgumentException("index must be >= 0, got: " + index);
	     }
	 }
	 
    /**
     * Holds per-element results in original input order.
     * Failed elements are represented as null in the output list,
     * with their exceptions accessible via {@link #getError(int)}.
     */
     public static final class MapResult<O> {
 
        private final List<TaskResult<O>> snapshot;
        private final int totalElements;
        
        private final int successCount;
        private final int failureCount;
        private final int cancelledCount;
        private final int notExecutedCount;
        private final Set<Integer> failedIndices;
        private final Set<Integer> cancelledIndices;
        private final Set<Integer> notExecutedIndices;
 
        MapResult(List<TaskResult<O>> snapshot, int totalElements) {
            this.snapshot = Collections.unmodifiableList(snapshot);
            this.totalElements = totalElements;
            int successes 	= 0;
            int failures 	= 0;
            int cancelled 	= 0;
            int notExecuted = 0;
            Set<Integer> failedSet 		= new LinkedHashSet<>();
            Set<Integer> cancelledSet 	= new LinkedHashSet<>();
            Set<Integer> notExecSet 	= new LinkedHashSet<>();
            
            for (int i = 0; i< snapshot.size(); i++) {
            	TaskResult<O> r = snapshot.get(i);
            	if (r.isSuccess()) { successes++; }
            	else if (r.isCancelled()) { cancelled++; cancelledSet.add(i); }
            	else if (r.isNotExecuted()) { notExecuted++; notExecSet.add(i); }
            	else { failures++; failedSet.add(i); }
            }
            this.successCount = successes;
            this.failureCount = failures;
            this.cancelledCount = cancelled;
            this.notExecutedCount = notExecuted;
            this.failedIndices = Collections.unmodifiableSet(failedSet);
            this.cancelledIndices = Collections.unmodifiableSet(cancelledSet);
            this.notExecutedIndices = Collections.unmodifiableSet(notExecSet);
        }
        
        /** Returns the ElementResult at index - always Success or Failure, never null. */
        public TaskResult<O> get(int index) {
            return snapshot.get(index);
        }
 
        /** True if element at index succeeded (including null value). */
        public boolean succeeded(int index) { return get(index).isSuccess(); }
 
        /** True if element at index failed. */
        public boolean failed(int index) { return !get(index).isSuccess(); }
 
        public boolean cancelled(int index)    { return get(index).isCancelled(); }
        public boolean notExecuted(int index)  { return get(index).isNotExecuted(); }
        
        /** Returns the output value for a successful element. Throws if failed. */
        @SuppressWarnings("unchecked")
        public O getValue(int index) {
            if (get(index) instanceof Success<O> s) return s.value();
            throw new IllegalStateException(
                "Element[" + index + "] failed - call getError() instead"
            );
        }
 
        /** Returns the error for a failed element. Null if it succeeded. */
        public Throwable getError(int index) {
            return get(index) instanceof Failure<O> f ? f.error() : null;
        }
 
        /**
         * All successful outputs in original input order.
         * Failed elements are skipped entirely - no nulls.
         */
        public List<O> toSuccessfulList() {
            List<O> list = new ArrayList<>();
            for (int i = 0 ; i < totalElements; i++) {
                if (get(i) instanceof Success<O> s) list.add(s.value());
            }
            return Collections.unmodifiableList(list);
        }
 
        /**
         * All ElementResults in original order - gives full access to both
         * success values and failure errors via pattern matching.
         *
         * <pre>{@code
         *   for (var r : result.toResultList()) {
         *       switch (r) {
         *           case Success<String> s -> process(s.value());
         *           case Failure<String> f -> log(f.error());
         *       }
         *   }
         * }</pre>
         */
        public List<TaskResult<O>> toResultList() { return snapshot; }
 
        /** Indices of elements that failed. */
        public Set<Integer> failedIndices() { return failedIndices; }
        /** Indices of elements that cancelled. */
        public Set<Integer> cancelledIndices() { return cancelledIndices; }
        /** Indices of elements that did not execute. */
        public Set<Integer> notExecutedIndices() { return notExecutedIndices; }
        public boolean hasErrors() { return !failedIndices().isEmpty(); }
        public boolean hasIncomplete() { return !cancelledIndices().isEmpty() || !notExecutedIndices().isEmpty(); }
        
        public int successCount() { return successCount; }
        public int errorCount()       { return failureCount; }
        public int cancelledCount()   { return cancelledCount; }
        public int notExecutedCount() { return notExecutedCount; }
        public int totalCount()       { return totalElements; }
 
        @Override
        public String toString() {
            return "MapResult{total=" + totalElements
                + ", succeeded=" + successCount
                + ", failed=" + failureCount
                + ", cancelled=" + cancelledCount
                + ", notExecuted=" + notExecutedCount + "}";
        }
    }
 
    private final String name;
    private final BiFunction<I, NodeContext, O> mapFn;
    private final long globalTimeoutMs;
    private final boolean continueOnError;
    private final long completionPollTimeoutMs;
    private final int maxInFlight;         // bounded window size(0 = use runtime limiter permits)
    private final long cancellationGraceMs; // grace period for awaitCancellation
 
    private MapNode(
            String name,
            BiFunction<I, NodeContext, O> mapFn,
            long globalTimeoutMs,
            long completionPollTimeoutMs,
            boolean continueOnError,
            int maxInFlight,
            long cancellationGraceMs
    ) {
        this.name = Objects.requireNonNull(name);
        this.mapFn = Objects.requireNonNull(mapFn);
        this.globalTimeoutMs = globalTimeoutMs;
        this.completionPollTimeoutMs = completionPollTimeoutMs;
        this.continueOnError = continueOnError;
        this.maxInFlight = maxInFlight;
        this.cancellationGraceMs = cancellationGraceMs;
    }
 
    @Override
    public MapResult<O> process(Iterable<I> input, NodeContext context) {
    	// In MapNode.process()
    	if (maxInFlight <= 0) {
    	    context.getLogger().warning(
    	        "[MapNode:" + name + "] No maxInFlight set. " +
    	        "If your mapWith() lambda makes LLM calls, set maxInFlight " +
    	        "to prevent API rate limiting. Recommended: maxInFlight(3)"
    	    );
    	}
    	// v0.6+ will use reflection to detect llm calls inside lambda
    	 List<I> elements = new ArrayList<>();
         input.forEach(elements::add);
         if (elements.isEmpty()) {
             context.getLogger().info("[MapNode:" + name + "] Empty input.");
             return new MapResult<O>(Collections.<TaskResult<O>>emptyList(), 0);
         }
  
         ExecutionRuntime runtime = context.getRuntime();
         if (runtime == null) {
             context.getLogger().warning(
                 "[MapNode:" + name + "] No ExecutionRuntime — running sequentially."
             );
             return runSequential(elements, context);
         }
  
         ExecutorService executor = runtime.getExecutor();
         boolean failFast         = runtime.getFailureMode() == ExecutionRuntime.FailureMode.FAIL_FAST
                                    && !continueOnError;
  
         int windowSize = maxInFlight > 0 ? maxInFlight : runtime.getMaxConcurrency();
         if (windowSize <= 0) windowSize = Runtime.getRuntime().availableProcessors();
         context.getLogger().info(
        		 "[MapNode:" + name + "] Mapping " + elements.size()
                 	+ " elements, window=" + windowSize
                    + ", globalTimeout=" + globalTimeoutMs + "ms"
                    + (completionPollTimeoutMs > 0 ? ", pollTimeout=" + completionPollTimeoutMs + "ms" : "")
         );
         AtomicReferenceArray<TaskResult<O>> results = new AtomicReferenceArray<>(elements.size());
         long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(globalTimeoutMs);
         ExecutorCompletionService<IndexedResult<O>> ecs = new ExecutorCompletionService<>(executor);
         List<Future<IndexedResult<O>>> submittedFutures = new ArrayList<>(elements.size());
  
         int submitted = 0;
         int collected = 0;
         boolean deadlineExceeded = false;
  
         // Seed the initial window - limiter acquired before submit
         while (submitted < elements.size() && submitted < windowSize) {
             Future<IndexedResult<O>> f = submitOne(submitted, elements, ecs, context);
             submittedFutures.add(f);
             submitted++;
         }
         // Drain: collect one, submit one
         while (collected < elements.size()) {
             if (System.nanoTime() > deadline) {
                 deadlineExceeded = true;
                 context.getLogger().warning(
                     "[MapNode:" + name + "] Global deadline exceeded after "
                         + collected + "/" + elements.size() + " collected."
                 );
                 break;
             }
  
             long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
             long pollTimeout = completionPollTimeoutMs > 0
                 ? Math.min(completionPollTimeoutMs, remainingMs)
                 : remainingMs;
  
             Future<IndexedResult<O>> future;
             try {
                 future = ecs.poll(Math.max(pollTimeout, 1), TimeUnit.MILLISECONDS);
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 cancelAll(submittedFutures, context);
                 throw new RuntimeException("[MapNode:" + name + "] interrupted", e);
             }
  
             if (future == null) {
                 deadlineExceeded = true;
                 context.getLogger().warning(
                     "[MapNode:" + name + "] Poll timed out after " + pollTimeout + "ms."
                 );
                 break;
             }
  
             IndexedResult<O> indexed;
             try {
                 indexed = future.get();
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 cancelAll(submittedFutures, context);
                 throw new RuntimeException("[MapNode:" + name + "] interrupted collecting result", e);
             } catch (ExecutionException e) {
                 // task body catches all exceptions internally, this path means
                 // something went deeply wrong (e.g. Error, not Exception). Cancel everything and propagate immediately.
                 cancelAll(submittedFutures, context);
                 Throwable cause = e.getCause() != null ? e.getCause() : e;
                 throw new RuntimeException(
                     "[MapNode:" + name + "] unexpected task-level failure — "
                         + "this should not happen as task body catches all Exception types. "
                         + "Possible cause: Error (OOM, StackOverflow) escaped the task.",
                     cause
                 );
             }
  
             results.set(indexed.index(), indexed.result());
             collected++;
  
             // cancel ALL before throwing on fail-fast
             if (failFast && !indexed.result().isSuccess()) {
                 cancelAll(submittedFutures, context);
                 throw new MapElementException(
                     name, indexed.index(), ((Failure<O>) indexed.result()).error()
                 );
             }
  
             // Submit next - limiter already acquired in submitOne before submit
             if (submitted < elements.size()) {
                 Future<IndexedResult<O>> f = submitOne(submitted, elements, ecs, context);
                 submittedFutures.add(f);
                 submitted++;
             }
         }
         // cancel in-flight, mark with distinct Cancelled vs NotExecuted
         if (deadlineExceeded) {
             cancelAll(submittedFutures, context); 
             String timeoutReason = "Global timeout of " + globalTimeoutMs + "ms exceeded";
             for (int i = 0; i < elements.size(); i++) {
                 // distinguish submitted-but-cancelled from never-submitted
                 if (i < submitted) {
                     // was submitted - running or waiting - now cancelled
                     results.compareAndSet(i, null, new Cancelled<>(timeoutReason));
                 } else {
                     // never made it into the window
                     results.compareAndSet(i, null, new NotExecuted<>(timeoutReason));
                 }
             }
  
             if (failFast) {
                 throw new RuntimeException(
                     "[MapNode:" + name + "] timed out after " + globalTimeoutMs + "ms. "
                         + "Cancelled " + (submitted - collected) + " in-flight tasks. "
                         + "Note: tasks ignoring thread interruption may still be running - "
                         + "ensure your mapper respects interruption or configures SDK-level timeouts."
                 );
             }
         }
  
         // Freeze snapshot, no more mutations possible after this point
         List<TaskResult<O>> snapshot = freezeSnapshot(results, elements.size());
         MapResult<O> result = new MapResult<>(snapshot, elements.size());
         context.getLogger().info(
             "[MapNode:" + name + "] Done - " + result
         );
         return result;
    }
    
    /**
     * Acquires limiter before submit (submission throttling not worker blocking).
     */
    private Future<IndexedResult<O>> submitOne(
            int index,
            List<I> elements,
            ExecutorCompletionService<IndexedResult<O>> ecs,
            NodeContext parentContext
    ) {
        final I element = elements.get(index);
        // Create isolated child context for this element
        final NodeContext childContext = parentContext.child("element-" + index);
        try {
            return ecs.submit(() -> {
                try {
                    O output = mapFn.apply(element, childContext);
                    return new IndexedResult<>(index, new Success<>(output));
                } catch (Throwable t) {
                    parentContext.getLogger().warning(
                        "[MapNode:" + name + "] Element[" + index + "] failed: " + t.getMessage()
                    );
                    if (t instanceof VirtualMachineError vme) throw vme;
                    return new IndexedResult<>(index, new Failure<>(t));
                }
            });
        } catch (RuntimeException e) {
            // ecs.submit() threw (executor shutdown, rejection, OOM)
            throw e;
        }
    }
    
    /** skip already-done futures */
    private void cancelAll(List<Future<IndexedResult<O>>> futures, NodeContext context) {
    	int cancelled = 0;
        for (Future<IndexedResult<O>> f : futures) {
            if (!f.isDone()) {
                f.cancel(true);
                cancelled++;
            }
        }
        if (cancelled == 0 || cancellationGraceMs <= 0) return;
        // Give interrupted tasks a brief window to notice the interrupt and exit cleanly.
        // This does not guarantee termination, interruption-insensitive mappers
        // (blocking HTTP, Thread.sleep ignoring interrupt) will keep running past this.
        // Grace period is a best-effort courtesy, not a hard barrier.
        context.getLogger().info(
            "[MapNode:" + name + "] Cancelled " + cancelled + " task(s). "
                + "Waiting up to " + cancellationGraceMs + "ms grace period for clean exit."
        );

        long graceDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(cancellationGraceMs);
        for (Future<IndexedResult<O>> f : futures) {
            if (f.isDone()) continue;
            long remaining = TimeUnit.NANOSECONDS.toMillis(graceDeadline - System.nanoTime());
            if (remaining <= 0) break;
            try {
                f.get(remaining, TimeUnit.MILLISECONDS);
            } catch (CancellationException ignored) {
                // expected - task was cancelled
            } catch (TimeoutException ignored) {
                // grace expired - task still running, nothing more we can do
            } catch (ExecutionException ignored) {
                // task threw after cancel - still counts as done
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long stillRunning = futures.stream().filter(f -> !f.isDone()).count();
        if (stillRunning > 0) {
            context.getLogger().warning(
                "[MapNode:" + name + "] " + stillRunning + " task(s) still running after grace period. "
                    + "These are interruption-insensitive - configure SDK-level timeouts in your mapper."
            );
        }
    }
    
    /** Drains AtomicReferenceArray into immutable snapshot. */
    private List<TaskResult<O>> freezeSnapshot(
            AtomicReferenceArray<TaskResult<O>> arr, int size
    ) {
        List<TaskResult<O>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            TaskResult<O> r = arr.get(i);
            list.add(r != null ? r : new NotExecuted<>("Result slot empty after collection"));
        }
        return list;
    }
    
    /** Fallback when no runtime is available - runs elements sequentially. */
    private MapResult<O> runSequential(List<I> elements, NodeContext context) {
    	long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(globalTimeoutMs);
    	List<TaskResult<O>> results = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
        	if (System.nanoTime() > deadline) {
                String reason = "Sequential timeout after " + globalTimeoutMs + "ms";
                context.getLogger().warning(
                    "[MapNode:" + name + "] Sequential deadline exceeded at element[" + i + "]"
                );
                // mark current and remaining as NotExecuted
                while (results.size() < elements.size()) {
                    results.add(new NotExecuted<>(reason));
                }
                break;
            }
        	// Create child context per element, same as parallel path
            NodeContext childCtx = context.child("element-" + i);
            try {
                results.add(new Success<>(mapFn.apply(elements.get(i), childCtx)));
            } catch (Exception e) {
            	results.add(new Failure<>(e));
                if (!continueOnError) {
                	// fill remaining as NotExecuted before throwing
                	String reason = "Aborted after element[" + i + "] failed";
                	context.getLogger().warning("[MapNode:" + name + "] " + reason + ": " + e.getMessage());
                    throw new MapElementException(name, i, e);
                }
            }
        }
        return new MapResult<O>(Collections.unmodifiableList(results), elements.size());
    }
    
    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }
 
    public static final class Builder<I, O> {
 
        private BiFunction<I, NodeContext, O> mapFn;
        private long globalTimeoutMs         = 60_000L;
        private long completionPollTimeoutMs = 0L;
        private boolean continueOnError      = false;
        private int maxInFlight              = 0;
        private long cancellationGraceMs 	 = 500L;
 
        public Builder<I, O> mapWith(BiFunction<I, NodeContext, O> fn) {
            this.mapFn = Objects.requireNonNull(fn);;
            return this;
        }
        
        public Builder<I, O> mapWith(Function<I, O> fn) {
        	Objects.requireNonNull(fn);
            this.mapFn = (element, ctx) -> fn.apply(element);
            return this;
        }
 
        /** Total time budget for the entire map operation. Default: 60s. */
        public Builder<I, O> timeout(long duration, TimeUnit unit) {
            this.globalTimeoutMs = unit.toMillis(duration);
            return this;
        }
 
        /**
         * Controls how long each ECS.poll() waits before declaring a timeout.
         * NOT true per-element cancellation - a slow mapper continues running
         * until thread interruption is respected or the SDK timeout fires.
         * Mapper functions should be interruption-aware for this to work correctly.
         */
        public Builder<I, O> completionPollTimeout(long duration, TimeUnit unit) {
            this.completionPollTimeoutMs = unit.toMillis(duration);
            return this;
        }
 
        /**
         * Max elements in-flight simultaneously.
         * Default: 0 - uses runtime.getMaxConcurrency().
         */
        public Builder<I, O> maxInFlight(int max) {
            if (max < 1) throw new IllegalArgumentException("maxInFlight must be >= 1");
            this.maxInFlight = max;
            return this;
        }
 
        /**
         * Capture failed elements in result instead of aborting on first failure.
         * Note: if runtime.getFailureMode() == FAIL_FAST, this flag overrides it
         * for MapNode - continueOnError disables fail-fast behavior for this node.
         */
        public Builder<I, O> continueOnError() {
            this.continueOnError = true;
            return this;
        }
        
        public Builder<I, O> cancellationGrace(long duration, TimeUnit unit) {
        	this.cancellationGraceMs = unit.toMillis(duration);
        	return this;
        }
 
        public MapNode<I, O> build(String nodeName) {
            if (mapFn == null) {
                throw new IllegalStateException("MapNode [" + nodeName + "] requires mapWith()");
            }
            return new MapNode<>(nodeName, mapFn, globalTimeoutMs,
                completionPollTimeoutMs, continueOnError, maxInFlight, cancellationGraceMs);
        }
    }
    
    public static class MapElementException extends RuntimeException {
        private final int elementIndex;
 
        public MapElementException(String nodeName, int index, Throwable cause) {
            super("MapNode [" + nodeName + "] element[" + index + "] failed", cause);
            this.elementIndex = index;
        }
 
        public int getElementIndex() { return elementIndex; }
    }
}
