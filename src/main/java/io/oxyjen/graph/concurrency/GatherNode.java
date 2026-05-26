package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.gather.GatherCollectors;

public class GatherNode implements NodePlugin<Object, GatherNode.GatherResult> {

    @Override
    public GatherResult process(Object input, NodeContext context) {
    	long start = System.currentTimeMillis();
    	NormalizeResult normalized = normalize(input, context);
    	context.getLogger().info(
    			"[GatherNode:" + name + "] Collected " + normalized.items.size()
                    + " items from " + normalized.sourceCount + " source(s)"
                    + " using mode=" + collectionMode
        );
    	List<Object> filtered = applyFilter(normalized.items);
        List<Object> sorted = applySort(filtered);
        List<Object> limited = applyLimit(sorted);
        List<Object> transformed = applyTransform(limited);
        List<Object> finalItems;
        Object aggregated;
        if (groupByFn != null) {
        	Map<Object, List<Object>> grouped = applyGroupBy(transformed);
        	aggregated = grouped;
            finalItems = transformed;
        } else {
        	finalItems = transformed;
        	aggregated = aggregate(Collections.unmodifiableList(finalItems));
        }
        long elapsed = System.currentTimeMillis() - start;
        context.getLogger().info(
            "[GatherNode:" + name + "] After filter/sort/limit: " + finalItems.size()
                + " items. Dropped: " + (normalized.items.size() - finalItems.size())
                + ", processingTimeMs=" + elapsed
        );
        GatherResult result = new GatherResult(finalItems, aggregated, normalized.items.size(), elapsed, normalized.sourceCount);
        // Store result in typed memory namespace - avoids string-key collision
        // and hidden global state mutation on context.data
        //context.memory("gather").put(name, result);
        return result;
    }

	public static final class GatherResult {

        private final List<Object> items;
        private final Object aggregated;
        private final int totalCollected; // before filter/limit
        private final int filteredOut;
        private final long processingTimeMs;
        private final int sourceCount;

        GatherResult(List<Object> items, Object aggregated, int totalCollected, long processingTimeMs, int sourceCount) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.aggregated = aggregated;
            this.totalCollected = totalCollected;
            this.filteredOut = totalCollected - items.size();
            this.processingTimeMs = processingTimeMs;
            this.sourceCount = sourceCount;
        }

        /** The aggregated value (result of the aggregation strategy or custom fn). */
        @SuppressWarnings("unchecked")
        public <T> T value() { return (T) aggregated; }

        /** All items after filter/sort/limit, before aggregation. */
        @SuppressWarnings("unchecked")
        public <T> List<T> items() { return (List<T>) items; }

        /** How many items were collected from upstream before filtering. */
        public int totalCollected() { return totalCollected; }

        /** How many items were removed by the filter. */
        public int filteredOut() { return filteredOut; }

        /** How many items made it through to aggregation. */
        public int count() { return items.size(); }

        public boolean isEmpty() { return items.isEmpty(); }
        public long processingTimeMs() { return processingTimeMs; }
        public int sourceCount()       { return sourceCount; }

        @Override
        public String toString() {
            return "GatherResult{collected=" + totalCollected
                + ", filtered=" + filteredOut
                + ", kept=" + items.size() 
                + ", processingTimeMs=" + processingTimeMs
                + ", sources=" + sourceCount + "}";
        }
    }
	// Aggregation enum
	public enum Aggregation {
        LIST,
        FIRST,
        LAST,
        COUNT,
        SUM,
        AVERAGE,
        MAX,
        MIN
    }
	
	private final String name;
    private final CollectionMode collectionMode;
    private final Predicate<Object> filter;
    private final Comparator<Object> sorter;
    private final int limit;
    private final Function<Object, Object> transformer;
    private final Function<List<Object>, Object> aggregateFn;
 
    // optional groupBy key extractor - null means no grouping
    private final Function<Object, Object> groupByFn;
 
    @SuppressWarnings("unchecked")
    private GatherNode(
            String name,
            CollectionMode collectionMode,
            Predicate<?> filter,
            Comparator<?> sorter,
            int limit,
            Function<?, ?> transformer,
            Function<List<Object>, Object> aggregateFn,
            Function<?, ?> groupByFn
    ) {
        this.name           = Objects.requireNonNull(name, "name must not be null");
        this.collectionMode = Objects.requireNonNull(collectionMode, "collectionMode must not be null");
        this.filter         = filter      != null ? (Predicate<Object>) filter      : x -> true;
        this.sorter         = (Comparator<Object>) sorter;
        this.limit          = limit;
        this.transformer    = transformer != null ? (Function<Object, Object>) transformer : Function.identity();
        this.aggregateFn    = Objects.requireNonNull(aggregateFn, "aggregateFn must not be null");
        this.groupByFn      = (Function<Object, Object>) groupByFn;
    }
    
    /**
     * Extracts raw items from whatever input type arrives, using CollectionMode
     * and GatherCollectors so semantics are always explicit.
     *
     * Supported upstream types:
     *   - ParallelNode.ParallelResult
     *   - MapNode.MapResult
     *   - Iterable
     *   - single value (wrapped)
     *
     * CollectionMode controls which TaskResults are included for
     * ParallelResult and MapResult inputs.
     */
    @SuppressWarnings("unchecked")
    private NormalizeResult normalize(Object input, NodeContext context) {
        if (input instanceof ParallelNode.ParallelResult<?> parallel) {
            List<?> collected = GatherCollectors.collectFromParallelResult(parallel, collectionMode);
            return new NormalizeResult(new ArrayList<>(collected), 1);
        }
 
        if (input instanceof MapNode.MapResult<?> mapResult) {
            List<?> collected = GatherCollectors.collectFromMapResult(mapResult, collectionMode);
            return new NormalizeResult(new ArrayList<>(collected), 1);
        }
 
        if (input instanceof Iterable<?> iterable) {
            List<?> collected = GatherCollectors.collectFromIterable(iterable);
            return new NormalizeResult(new ArrayList<>(collected), 1);
        }
 
        // Single value, wrap in list
        if (input != null) {
            return new NormalizeResult(new ArrayList<>(List.of(input)), 1);
        }
 
        return new NormalizeResult(Collections.emptyList(), 0);
    }
    	
    /** Internal carrier - avoids polluting GatherResult with normalize-only fields. */
    private record NormalizeResult(List<Object> items, int sourceCount) {}
    
    /** Returns a new filtered list. Never mutates input. */
    private List<Object> applyFilter(List<Object> input) {
        if (filter == null) return new ArrayList<>(input);
        return input.stream()
            .filter(filter)
            .collect(Collectors.toCollection(ArrayList::new));
    }
 
    /** Returns a new sorted list. Never mutates input. */
    private List<Object> applySort(List<Object> input) {
        if (sorter == null) return new ArrayList<>(input);
        List<Object> copy = new ArrayList<>(input);
        copy.sort(sorter);
        return copy;
    }
    /** Returns a snapshot subList. Never mutates input. */
    private List<Object> applyLimit(List<Object> input) {
        if (limit <= 0 || input.size() <= limit) return new ArrayList<>(input);
        return new ArrayList<>(input.subList(0, limit));
    }
 
    /** Maps each element through the transformer. Never mutates input. */
    private List<Object> applyTransform(List<Object> input) {
        return input.stream()
            .map(transformer)
            .collect(Collectors.toCollection(ArrayList::new));
    }
    /**
     * Groups items by the key extractor. Returns a LinkedHashMap to preserve
     * insertion order of first-seen keys.
     */
    private Map<Object, List<Object>> applyGroupBy(List<Object> input) {
        Map<Object, List<Object>> grouped = new LinkedHashMap<>();
        for (Object item : input) {
            Object key = groupByFn.apply(item);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        grouped.replaceAll((k, v) -> Collections.unmodifiableList(v));
        return Collections.unmodifiableMap(grouped);
    }
 
    private Object aggregate(List<Object> items) {
        return aggregateFn.apply(items);
    }
 
    @Override
    public String getName() { return name; }
    
    public static Builder builder() { return new Builder(); }
    
    public static final class Builder {
        private CollectionMode collectionMode    = CollectionMode.SUCCESS_ONLY;
        private Predicate<?> filter              = null;
        private Comparator<?> sorter             = null;
        private int limit                        = 0;
        private Function<?, ?> transformer       = null;
        private Function<List<Object>, Object> aggregateFn = null;
        private Function<?, ?> groupByFn         = null;
        private Aggregation aggregationStrategy = Aggregation.LIST;
 
        /**
         * Controls which TaskResults are included when gathering from a
         * ParallelResult or MapResult.
         *
         * Defaults to SUCCESS_ONLY. Must be called explicitly if partial
         * failure results are needed downstream.
         */
        public Builder collectMode(CollectionMode mode) {
            this.collectionMode = Objects.requireNonNull(mode);
            return this;
        }
 
        public <T> Builder filter(Predicate<T> predicate) {
            this.filter = Objects.requireNonNull(predicate);
            return this;
        }
 
        public <T> Builder sortBy(Comparator<T> comparator) {
            this.sorter = Objects.requireNonNull(comparator);
            return this;
        }
 
        public Builder limit(int n) {
            if (n < 1) throw new IllegalArgumentException("limit must be >= 1");
            this.limit = n;
            return this;
        }
 
        public <I, O> Builder transform(Function<I, O> fn) {
            this.transformer = Objects.requireNonNull(fn);
            return this;
        }
 
        /**
         * Groups the transformed items by the given key extractor.
         * When groupBy is set, the aggregated value in GatherResult will be a
         * {@code Map<K, List<V>>} regardless of the aggregation strategy.
         */
        public <T, K> Builder groupBy(Function<T, K> keyExtractor) {
            this.groupByFn = Objects.requireNonNull(keyExtractor);
            return this;
        }
 
        /**
         * Built-in aggregation strategies. For numeric reducers (SUM, AVERAGE,
         * MAX, MIN) elements are cast to {@link Number}; a ClassCastException
         * will be thrown at runtime for non-numeric types.
         */
        public Builder aggregate(Aggregation strategy) {
        	this.aggregationStrategy = Objects.requireNonNull(strategy);
            this.aggregateFn = switch (strategy) {
                case LIST  -> items -> new ArrayList<>(items);
                case FIRST -> items -> items.isEmpty() ? null : items.get(0);
                case LAST  -> items -> items.isEmpty() ? null : items.get(items.size() - 1);
                case COUNT -> items -> items.size();
 
                case SUM -> items -> items.stream()
                    .map(o -> (Number) o)
                    .mapToDouble(Number::doubleValue)
                    .sum();
 
                case AVERAGE -> items -> items.isEmpty() ? 0.0 : items.stream()
                    .map(o -> (Number) o)
                    .mapToDouble(Number::doubleValue)
                    .average()
                    .orElse(0.0);
 
                case MAX -> items -> items.stream()
                    .map(o -> (Number) o)
                    .mapToDouble(Number::doubleValue)
                    .max()
                    .orElse(Double.NaN);
 
                case MIN -> items -> items.stream()
                    .map(o -> (Number) o)
                    .mapToDouble(Number::doubleValue)
                    .min()
                    .orElse(Double.NaN);
            };
            return this;
        }
 
        /** Fully custom aggregation over the final item list. */
        public Builder aggregateWith(Function<List<Object>, Object> fn) {
            this.aggregateFn = Objects.requireNonNull(fn);
            this.aggregationStrategy = null;
            return this;
        }
 
        public GatherNode build(String nodeName) {
            Objects.requireNonNull(nodeName, "nodeName must not be null");
            if (aggregateFn == null) {
                aggregateFn = items -> new ArrayList<>(items); // default: LIST
            }
            if (groupByFn != null && aggregationStrategy != null && aggregationStrategy != Aggregation.LIST) {
                throw new IllegalStateException(
                    "groupBy currently supports only LIST aggregation"
                );
            }
            return new GatherNode(nodeName, collectionMode, filter, sorter,
            		limit, transformer, aggregateFn, groupByFn
            );
        }
    }
}