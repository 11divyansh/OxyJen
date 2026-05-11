package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
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
public class MapNode<I, O> implements NodePlugin<Iterable<I>, MapNode.MapResult> {
 
	 public sealed interface ElementResult<T> permits Success, Failure {
		 boolean isSuccess();
	 }
	 
	 public static final class Success<T> implements ElementResult<T> {
		 private final T value;
	     public Success(T value) {
	         this.value = value;  // null is a valid success value
	     }
	     public T value() { return value; }
	 
	     @Override
	     public boolean isSuccess() { return true; }
	 
	     @Override
         public String toString() { return "Success(" + value + ")"; }
	 }
	 
	 public static final class Failure<T> implements ElementResult<T> {
	     private final Throwable error;
	     public Failure(Throwable error) {
	         this.error = Objects.requireNonNull(error);
         }
	     public Throwable error() { return error; }
	 
	     @Override
	     public boolean isSuccess() { return false; }
	     @Override
	     public String toString() { return "Failure(" + error.getMessage() + ")"; }
	 }
	 
    /**
     * Holds per-element results in original input order.
     * Failed elements are represented as null in the output list,
     * with their exceptions accessible via {@link #getError(int)}.
     */
    public static final class MapResult<O> {
 
        private final List<ElementResult<O>> results;       // null at index i = element i failed
 
        MapResult(List<ElementResult<O>> results) {
            this.results = results;
        }
        public List<ElementResult<O>> getResults() {
            return results;
        }
        /** Returns the ElementResult at index - always Success or Failure, never null. */
        public ElementResult<O> get(int index) {
            ElementResult<O> r = results.get(index);
            return r != null ? r : new Failure<>(new IllegalStateException("Element[" + index + "] never completed"));
        }
 
        /** True if element at index succeeded (including null value). */
        public boolean succeeded(int index) {
            return get(index).isSuccess();
        }
 
        /** True if element at index failed. */
        public boolean failed(int index) {
            return !get(index).isSuccess();
        }
 
        /** Returns the output value for a successful element. Throws if failed. */
        @SuppressWarnings("unchecked")
        public O getValue(int index) {
            ElementResult<O> r = get(index);
            if (r instanceof Success<O> s) return s.value();
            throw new IllegalStateException(
                "Element[" + index + "] failed — call getError() instead"
            );
        }
 
        /** Returns the error for a failed element. Null if it succeeded. */
        public Throwable getError(int index) {
            ElementResult<O> r = get(index);
            return r instanceof Failure<O> f ? f.error() : null;
        }
 
        /**
         * All successful outputs in original input order.
         * Failed elements are skipped entirely - no nulls.
         */
        public List<O> toList() {
            List<O> list = new ArrayList<>();
            for (ElementResult<O> result : results) {
                if (result instanceof Success<O> s) list.add(s.value());
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
        public List<ElementResult<O>> toResultList() {
            List<ElementResult<O>> list = new ArrayList<>();
            for (ElementResult<O> result : results) list.add(result);
            return Collections.unmodifiableList(list);
        }
 
        /** Indices of elements that failed. */
        public Set<Integer> failedIndices() {
            Set<Integer> failed = new LinkedHashSet<>();
            for (int i = 0; i < results.size(); i++) {
                if (!get(i).isSuccess()) failed.add(i);
            }
            return Collections.unmodifiableSet(failed);
        }
 
        public boolean hasErrors() {
            for (int i = 0; i < results.size(); i++) {
                if (!get(i).isSuccess()) return true;
            }
            return false;
        }
 
        @Override
        public String toString() {
            return "MapResult{total=" + results.size()
                + ", results=" + results + "}";
        }
    }
 
    private final String name;
    private final Function<I, O> mapFn;
    private final long timeoutMs;
    private final boolean continueOnError;
    private final int maxInFlight;         // bounded window size(0 = use runtime limiter permits)
 
    private MapNode(
            String name,
            Function<I, O> mapFn,
            long timeoutMs,
            boolean continueOnError,
            int maxInFlight
    ) {
        this.name = Objects.requireNonNull(name);
        this.mapFn = Objects.requireNonNull(mapFn);
        this.timeoutMs = timeoutMs;
        this.continueOnError = continueOnError;
        this.maxInFlight = maxInFlight;
    }
 
    @Override
    public MapResult process(Iterable<I> input, NodeContext context) {
    	 List<I> elements = new ArrayList<>();
         input.forEach(elements::add);
         if (elements == null || elements.isEmpty()) {
             context.getLogger().info("[MapNode:" + name + "] Empty input.");
             return new MapResult<>(List.of());
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
         return null;
    }
    
    /** Fallback when no runtime is available - runs elements sequentially. */
    private MapResult<O> runSequential(List<I> elements, NodeContext context) {
        AtomicReferenceArray<ElementResult<O>> results = new AtomicReferenceArray<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            try {
                results.set(i, new Success<>(mapFn.apply(elements.get(i))));
            } catch (Exception e) {
                results.set(i, new Failure<>(e));
                if (!continueOnError) {
                    throw new MapElementException(name, i, e);
                }
            }
        }
        return new MapResult<>(results, elements.size());
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