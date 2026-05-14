package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;

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
 
	 public sealed interface ElementResult<T> permits MapNode.Success, MapNode.Failure, MapNode.Cancelled, MapNode.NotExecuted {
		 boolean isSuccess();
		 default boolean isCancelled() { return false; }
		 default boolean isNotExecuted() { return false; }
	 }
	 
	 public static final class Success<T> implements ElementResult<T> {
		 private final T value;
	     public Success(T value) { this.value = value; }  // null is a valid success value}
	     public T value() { return value; }
	     @Override public boolean isSuccess() { return true; }
	     @Override public String toString() { return "Success(" + value + ")"; }
	 }
	 
	 public static final class Failure<T> implements ElementResult<T> {
	     private final Throwable error;
	     public Failure(Throwable error) { this.error = Objects.requireNonNull(error); }
	     public Throwable error() { return error; }
	     @Override public boolean isSuccess() { return false; }
	     @Override public String toString() { return "Failure(" + error.getMessage() + ")"; }
	 }
	 
	 /** Element was submitted and running but cancelled (timeout or fail-fast). */
	 public static final class Cancelled<T> implements ElementResult<T> {
	     private final String reason;
	     public Cancelled(String reason)        { this.reason = reason; }
	     public String reason()                 { return reason; }
	     @Override public boolean isSuccess()   { return false; }
	     @Override public boolean isCancelled() { return true; }
	     @Override public String toString()     { return "Cancelled(" + reason + ")"; }
     }
	 
	 /** Element was never submitted - window full when deadline hit. */
	 public static final class NotExecuted<T> implements ElementResult<T> {
	     private final String reason;
	     public NotExecuted(String reason)       { this.reason = reason; }
         public String reason()                  { return reason; }
	     @Override public boolean isSuccess()    { return false; }
	     @Override public boolean isNotExecuted(){ return true; }
	     @Override public String toString()      { return "NotExecuted(" + reason + ")"; }
	 }
	 
	 private record IndexedResult<O>(int index, ElementResult<O> result) {
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
 
        private final List<ElementResult<O>> snapshot;       // null at index i = element i failed
        private final int totalElements;
        
        private final int successCount;
        private final int failureCount;
        private final int cancelledCount;
        private final int notExecutedCount;
        private final Set<Integer> failedIndices;
        private final Set<Integer> cancelledIndices;
        private final Set<Integer> notExecutedIndices;
 
        MapResult(List<ElementResult<O>> snapshot, int totalElements) {
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
            	ElementResult<O> r = snapshot.get(i);
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
        public ElementResult<O> get(int index) {
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
        public List<ElementResult<O>> toResultList() { return snapshot; }
 
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
    private final Function<I, O> mapFn;
    private final long globalTimeoutMs;
    private final boolean continueOnError;
    private final long completionPollTimeoutMs;
    private final int maxInFlight;         // bounded window size(0 = use runtime limiter permits)
 
    private MapNode(
            String name,
            Function<I, O> mapFn,
            long globalTimeoutMs,
            long completionPollTimeoutMs,
            boolean continueOnError,
            int maxInFlight
    ) {
        this.name = Objects.requireNonNull(name);
        this.mapFn = Objects.requireNonNull(mapFn);
        this.globalTimeoutMs = globalTimeoutMs;
        this.completionPollTimeoutMs = completionPollTimeoutMs;
        this.continueOnError = continueOnError;
        this.maxInFlight = maxInFlight;
    }
 
    @Override
    public MapResult<O> process(Iterable<I> input, NodeContext context) {
    	 List<I> elements = new ArrayList<>();
         input.forEach(elements::add);
         if (elements.isEmpty()) {
             context.getLogger().info("[MapNode:" + name + "] Empty input.");
             return new MapResult<O>(Collections.<ElementResult<O>>emptyList(), 0);
         }
  
         ExecutionRuntime runtime = context.getRuntime();
         if (runtime == null) {
             context.getLogger().warning(
                 "[MapNode:" + name + "] No ExecutionRuntime — running sequentially."
             );
             return runSequential(elements, context);
         }
  
         ExecutorService executor = runtime.getExecutor();
         Semaphore limiter        = runtime.getLimiter();
         boolean failFast         = runtime.getFailureMode() == ExecutionRuntime.FailureMode.FAIL_FAST
                                    && !continueOnError;
  
         int windowSize = maxInFlight > 0 ? maxInFlight : limiter.availablePermits();
         if (windowSize <= 0) windowSize = Runtime.getRuntime().availableProcessors();
         context.getLogger().info(
        		 "[MapNode:" + name + "] Mapping " + elements.size()
                 	+ " elements, window=" + windowSize
                    + ", globalTimeout=" + globalTimeoutMs + "ms"
                    + (completionPollTimeoutMs > 0 ? ", pollTimeout=" + completionPollTimeoutMs + "ms" : "")
         );
         AtomicReferenceArray<ElementResult<O>> results = new AtomicReferenceArray<>(elements.size());
         long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(globalTimeoutMs);
         ExecutorCompletionService<IndexedResult<O>> ecs = new ExecutorCompletionService<>(executor);
         List<Future<IndexedResult<O>>> submittedFutures = new ArrayList<>(elements.size());
  
         int submitted = 0;
         int collected = 0;
         boolean deadlineExceeded = false;
  
         // Seed the initial window - limiter acquired before submit
         while (submitted < elements.size() && submitted < windowSize) {
             Future<IndexedResult<O>> f = submitOne(submitted, elements, ecs, limiter, context);
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
                 cancelAll(submittedFutures);
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
                 cancelAll(submittedFutures);
                 throw new RuntimeException("[MapNode:" + name + "] interrupted collecting result", e);
             } catch (ExecutionException e) {
                 // task body catches all exceptions internally, this path means
                 // something went deeply wrong (e.g. Error, not Exception). Cancel everything and propagate immediately.
                 cancelAll(submittedFutures);
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
                 cancelAll(submittedFutures);
                 throw new MapElementException(
                     name, indexed.index(), ((Failure<O>) indexed.result()).error()
                 );
             }
  
             // Submit next - limiter already acquired in submitOne before submit
             if (submitted < elements.size()) {
                 Future<IndexedResult<O>> f = submitOne(submitted, elements, ecs, limiter, context);
                 submittedFutures.add(f);
                 submitted++;
             }
         }
         // cancel in-flight, mark with distinct Cancelled vs NotExecuted
         if (deadlineExceeded) {
             cancelAll(submittedFutures); 
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
         List<ElementResult<O>> snapshot = freezeSnapshot(results, elements.size());
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
            Semaphore limiter,
            NodeContext context
    ) {
        final I element = elements.get(index);
 
        try {
            limiter.acquire(); // blocks calling (orchestration) thread, not worker
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[MapNode:" + name + "] interrupted acquiring limiter", e);
        }
 
        try {
            return ecs.submit(() -> {
                try {
                    O output = mapFn.apply(element);
                    return new IndexedResult<>(index, new Success<>(output));
                } catch (Throwable t) {
                    context.getLogger().warning(
                        "[MapNode:" + name + "] Element[" + index + "] failed: " + t.getMessage()
                    );
                    if (t instanceof VirtualMachineError vme) {
                        throw vme;
                    }
                    return new IndexedResult<>(index, new Failure<>(t));
                } finally {
                    limiter.release();
                }
            });
        } catch (RuntimeException e) {
            // ecs.submit() threw (executor shutdown, rejection, OOM)
            // permit was acquired but task never submitted - release it now
            limiter.release();
            throw e;
        }
    }
    
    /** skip already-done futures */
    private void cancelAll(List<Future<IndexedResult<O>>> futures) {
        for (Future<IndexedResult<O>> f : futures) {
            if (!f.isDone()) {
                f.cancel(true);
            }
        }
    }
    
    /** Drains AtomicReferenceArray into immutable snapshot. */
    private List<ElementResult<O>> freezeSnapshot(
            AtomicReferenceArray<ElementResult<O>> arr, int size
    ) {
        List<ElementResult<O>> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ElementResult<O> r = arr.get(i);
            list.add(r != null ? r : new NotExecuted<>("Result slot empty after collection"));
        }
        return list;
    }
    
    /** Fallback when no runtime is available - runs elements sequentially. */
    private MapResult<O> runSequential(List<I> elements, NodeContext context) {
    	long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(globalTimeoutMs);
    	List<ElementResult<O>> results = new ArrayList<>(elements.size());
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
            try {
                results.add(new Success<>(mapFn.apply(elements.get(i))));
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
 
        private Function<I, O> mapFn;
        private long globalTimeoutMs         = 60_000L;
        private long completionPollTimeoutMs = 0L;
        private boolean continueOnError      = false;
        private int maxInFlight              = 0;
 
        public Builder<I, O> mapWith(Function<I, O> fn) {
            this.mapFn = Objects.requireNonNull(fn);
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
 
        public MapNode<I, O> build(String nodeName) {
            if (mapFn == null) {
                throw new IllegalStateException("MapNode [" + nodeName + "] requires mapWith()");
            }
            return new MapNode<>(nodeName, mapFn, globalTimeoutMs,
                completionPollTimeoutMs, continueOnError, maxInFlight);
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