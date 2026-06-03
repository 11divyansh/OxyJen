package io.oxyjen.core.graphs.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.graph.concurrency.GatherNode;

class GatherNodeBuilderValidationTest {

    @Test
    void group_by_with_first_should_fail() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> GatherNode.builder()
                .groupBy((String s) -> s.charAt(0))
                .aggregate(GatherNode.Aggregation.FIRST)
                .build("gather")
        );
        assertTrue(ex.getMessage().contains("groupBy"));
    }

    @Test
    void group_by_with_sum_should_fail() {
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> GatherNode.builder()
                .groupBy((Integer i) -> i % 2)
                .aggregate(GatherNode.Aggregation.SUM)
                .build("gather")
        );

        assertTrue(
            ex.getMessage().contains("groupBy")
        );
    }

    @Test
    void group_by_with_list_should_succeed() {
        GatherNode node = GatherNode.builder()
            .groupBy((String s) -> s.charAt(0))
            .aggregate(GatherNode.Aggregation.LIST)
            .build("gather");
        assertNotNull(node);
    }

    @Test
    void limit_zero_should_fail() {
        assertThrows(
            IllegalArgumentException.class,
            () -> GatherNode.builder()
                .limit(0)
        );
    }

    @Test
    void null_collection_mode_should_fail() {
        assertThrows(
            NullPointerException.class,
            () -> GatherNode.builder()
                .collectMode(null)
        );
    }

    @Test
    void null_aggregate_with_should_fail() {
        assertThrows(
            NullPointerException.class,
            () -> GatherNode.builder()
                .aggregateWith(null)
        );
    }

    @Test
    void default_aggregation_should_be_list() {
        GatherNode node = GatherNode.builder().build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1, 2, 3), ctx);
        assertEquals(
            List.of(1, 2, 3),
            result.value()
        );
    }
}