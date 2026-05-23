package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class GatherNode implements NodePlugin<Object, GatherNode.GatherResult> {

    @Override
    public GatherResult process(Object input, NodeContext context) {
        List<Object> items;
        if (input instanceof List<?> list) {
            items = new ArrayList<>(list);
        } else if (input == null) {
            items = List.of();
        } else {
            items = List.of(input);
        }
        return new GatherResult(items, items, items.size());
    }

	public static final class GatherResult {

        private final List<Object> items;
        private final Object aggregated;
        private final int totalCollected; // before filter/limit
        private final int filteredOut;

        GatherResult(List<Object> items, Object aggregated, int totalCollected) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.aggregated = aggregated;
            this.totalCollected = totalCollected;
            this.filteredOut = totalCollected - items.size();
        }

        /** The aggregated value (result of the aggregation strategy or custom fn). */
        @SuppressWarnings("unchecked")
        public <T> T value() {
            return (T) aggregated;
        }

        /** All items after filter/sort/limit, before aggregation. */
        @SuppressWarnings("unchecked")
        public <T> List<T> items() {
            return (List<T>) items;
        }

        /** How many items were collected from upstream before filtering. */
        public int totalCollected() {
            return totalCollected;
        }

        /** How many items were removed by the filter. */
        public int filteredOut() {
            return filteredOut;
        }

        /** How many items made it through to aggregation. */
        public int count() {
            return items.size();
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }

        @Override
        public String toString() {
            return "GatherResult{collected=" + totalCollected
                + ", filtered=" + filteredOut
                + ", kept=" + items.size() + "}";
        }
    }
}
