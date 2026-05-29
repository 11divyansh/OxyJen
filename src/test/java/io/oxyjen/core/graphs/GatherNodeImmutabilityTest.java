package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;

class GatherNodeImmutabilityTest {

    @Test
    void modifying_source_list_should_not_affect_gather_result() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
        GatherNode node = GatherNode.builder().build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(source, ctx);
        source.add(4);
        assertEquals(List.of(1, 2, 3), result.items());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void modifying_grouped_map_should_fail() {
        GatherNode node = GatherNode.builder()
            .groupBy((String s) -> s.charAt(0))
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of("apple", "ape"), ctx);
        Map grouped =(Map) result.value();
        assertThrows(UnsupportedOperationException.class, () -> grouped.put('x', List.of("x")));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void modifying_gather_result_items_should_fail() {
        GatherNode node = GatherNode.builder().build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process(List.of(1, 2, 3), ctx);
        List items = result.items();
        assertThrows(UnsupportedOperationException.class, () -> items.add(999));
    }

    @Test
    void modifying_parallel_result_after_gather_should_not_affect_result() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("A"));
        results.put("b", new Success<>("B"));
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(results, List.of("a", "b"));
        GatherNode node = GatherNode.builder()
            .collectMode(CollectionMode.SUCCESS_ONLY)
            .build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult gathered =node.process(parallel, ctx);
        results.put("c", new Success<>("MUTATED"));
        assertEquals(List.of("A", "B"), gathered.items() );
    }
}