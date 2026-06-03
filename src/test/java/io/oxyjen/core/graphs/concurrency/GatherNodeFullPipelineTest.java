package io.oxyjen.core.graphs.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.result.Cancelled;
import io.oxyjen.execution.result.Failure;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;

class GatherNodeFullPipelineTest {

    @Test
    void filter_sort_limit_aggregate_should_execute_full_pipeline() {
        GatherNode node = GatherNode.builder()
            .filter((Integer i) -> i % 2 == 0)
            .sortBy((Integer a, Integer b) -> b.compareTo(a))
            .limit(1)
            .aggregate(GatherNode.Aggregation.FIRST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(5, 1, 3, 2, 4), ctx);
        assertEquals(4, ((Number) result.value()).longValue());
    }

    @Test
    void transform_before_aggregation_should_sum_transformed_values() {
        GatherNode node = GatherNode.builder()
            .transform((String s) -> s.length())
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of("a", "bb", "ccc"), ctx);
        assertEquals(6.0, ((Number) result.value()).doubleValue());
    }

    @Test
    void grouping_after_transform_should_group_transformed_values() {
        GatherNode node = GatherNode.builder()
            .transform((String s) -> s.toUpperCase())
            .groupBy((String s) -> s.charAt(0))
            .aggregate(GatherNode.Aggregation.LIST)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of("apple", "ape", "banana"), ctx);
        Map<?, ?> grouped = (Map<?, ?>) result.value();
        assertEquals(List.of("APPLE", "APE"), grouped.get('A'));
        assertEquals(List.of("BANANA"), grouped.get('B'));
    }

    @Test
    void collection_mode_should_interact_correctly_with_transform() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("apple"));
        results.put("b", new Success<>("banana"));
        results.put("c", new Failure<>(new RuntimeException("boom")));
        results.put("d", new Cancelled<>("cancelled"));
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                results,
                List.of("a", "b", "c", "d"));
        GatherNode node = GatherNode.builder()
            .collectMode(CollectionMode.SUCCESS_ONLY)
            .transform((String s) -> s.length())
            .aggregate(GatherNode.Aggregation.SUM)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(parallel, ctx);
        assertEquals(11.0,((Number) result.value()).doubleValue());
    }
}