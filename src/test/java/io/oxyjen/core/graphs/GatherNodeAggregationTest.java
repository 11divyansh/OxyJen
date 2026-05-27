package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.graph.concurrency.GatherNode;

class GatherNodeAggregationTest {

    @Test
    void list_aggregation_should_return_snapshot_list() {
        List<Integer> source =new ArrayList<>(List.of(1, 2, 3));
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.LIST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(source, ctx);
        List<?> aggregated = (List<?>) result.value();
        source.add(999);
        assertEquals(List.of(1, 2, 3), aggregated);
    }

    @Test
    void first_should_return_first_item() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.FIRST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of("a", "b", "c"), ctx);
        assertEquals("a", result.value());
    }

    @Test
    void first_on_empty_should_return_null() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.FIRST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(Collections.emptyList(), ctx);
        assertNull(result.value());
    }

    @Test
    void last_should_return_last_item() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.LAST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of("a", "b", "c"), ctx);
        assertEquals("c", result.value());
    }

    @Test
    void count_should_return_item_count() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.COUNT)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1, 2, 3, 4), ctx);
        assertEquals(4L, ((Number) result.value()).longValue());
    }

    @Test
    void sum_should_sum_integers() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1, 2, 3, 4), ctx);
        assertEquals(10.0, result.value());
    }

    @Test
    void sum_should_sum_doubles() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1.5, 2.5, 3.0), ctx);
        assertEquals(7.0, result.value());
    }

    @Test
    void sum_empty_should_return_zero() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(Collections.emptyList(), ctx);
        assertEquals(0.0, result.value());
    }

    @Test
    void average_should_compute_integer_average() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.AVERAGE)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(2, 4, 6, 8), ctx);
        assertEquals(5.0, result.value());
    }

    @Test
    void average_empty_should_return_zero() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.AVERAGE)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(Collections.emptyList(), ctx);
        assertEquals(0.0, result.value());
    }

    @Test
    void max_should_return_max_integer() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.MAX)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1, 7, 3, 2), ctx);
        assertEquals(7.0, result.value());
    }

    @Test
    void min_should_return_min_integer() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.MIN)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(5, 2, 8, 1), ctx);
        assertEquals(1.0, result.value());
    }

    @Test
    void max_empty_should_return_nan() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.MAX)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(Collections.emptyList(), ctx);
        assertTrue(Double.isNaN((Double) result.value()));
    }

    @Test
    void non_numeric_aggregation_should_fail() {
        GatherNode node = GatherNode.builder()
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        assertThrows(
            ClassCastException.class,
            () -> node.process(
                List.of("a", "b", "c"),
                ctx
            )
        );
    }
}