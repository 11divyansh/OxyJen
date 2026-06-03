package io.oxyjen.core.graphs.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.gather.GatherCollectors;
import io.oxyjen.execution.result.Cancelled;
import io.oxyjen.execution.result.Failure;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;

class GatherNodePipelineTest {

	    @Test   
	    void filter_should_remove_elements_correctly() {
	        GatherNode node = GatherNode.builder()
	            .filter((Integer i) -> i % 2 == 0)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(1, 2, 3, 4), ctx);
	        assertEquals(List.of(2, 4), result.items());
	    }

	    @Test
	    void filter_should_preserve_ordering() {
	        GatherNode node = GatherNode.builder()
	            .filter((Integer i) -> i % 2 == 0)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(4, 2, 6, 1, 8), ctx);
	        assertEquals(List.of(4, 2, 6, 8), result.items());
	    }

	    @Test
	    void sort_should_sort_ascending() {
	        GatherNode node = GatherNode.builder()
	            .sortBy(Integer::compareTo)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(4, 1, 3, 2), ctx);
	        assertEquals(List.of(1, 2, 3, 4), result.items());
	    }

	    @Test
	    void sort_should_sort_descending() {
	        GatherNode node = GatherNode.builder()
	            .sortBy((Integer a, Integer b) -> b.compareTo(a))
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(1, 4, 2, 3), ctx);
	        assertEquals(List.of(4, 3, 2, 1), result.items());
	    }

	    @Test
	    void sort_should_not_mutate_original_input() {
	        List<Integer> original = new ArrayList<>(List.of(4, 1, 3, 2));
	        GatherNode node = GatherNode.builder()
	            .sortBy(Integer::compareTo)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        node.process(original, ctx);
	        assertEquals(List.of(4, 1, 3, 2), original);
	    }

	    @Test
	    void limit_should_truncate_correctly() {
	        GatherNode node = GatherNode.builder()
	            .limit(2)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(1, 2, 3, 4), ctx);
	        assertEquals(List.of(1, 2), result.items());
	    }

	    @Test
	    void limit_larger_than_list_should_preserve_all() {
	        GatherNode node = GatherNode.builder()
	            .limit(10)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of(1, 2, 3), ctx);
	        assertEquals(List.of(1, 2, 3), result.items());
	    }

	    @Test
	    void limit_should_return_snapshot_not_view() {
	        List<Integer> original = new ArrayList<>(List.of(1, 2, 3, 4));
	        GatherNode node = GatherNode.builder()
	            .limit(2)
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(original, ctx);
	        original.set(0, 999);
	        assertEquals(List.of(1, 2), result.items());
	    }

	    @Test
	    void transform_should_map_values() {
	        GatherNode node = GatherNode.builder()
	            .transform((String s) -> s.length())
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of("a", "bb"), ctx);
	        assertEquals(List.of(1, 2), result.items());
	    }

	    @Test
	    void transform_should_preserve_count_and_order() {
	        GatherNode node = GatherNode.builder()
	            .transform((String s) -> s.toUpperCase())
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of("a", "b", "c"), ctx);
	        assertEquals(3, result.count());
	        assertEquals(List.of("A", "B", "C"), result.items());
	    }

	    @Test
	    void group_by_should_partition_correctly() {
	        GatherNode node = GatherNode.builder()
	            .groupBy((String s) -> s.charAt(0))
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of("apple", "ape", "banana"), ctx);
	        Map<?, ?> grouped = result.value();
	        assertEquals(
	            List.of("apple", "ape"),
	            grouped.get('a')
	        );

	        assertEquals(
	            List.of("banana"),
	            grouped.get('b')
	        );
	    }

	    @Test
	    void group_by_should_preserve_insertion_order() {
	        GatherNode node = GatherNode.builder()
	            .groupBy((String s) -> s.charAt(0))
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(
	                List.of("banana", "apple", "ape"),
	                ctx
	            );
	        Map<?, ?> grouped = result.value();
	        List<?> keys = new ArrayList<>(grouped.keySet());
	        assertEquals(List.of('b', 'a'), keys);
	    }

	    @Test
	    @SuppressWarnings({"rawtypes", "unchecked"})
	    void group_by_should_return_immutable_map() {
	        GatherNode node = GatherNode.builder()
	            .groupBy((String s) -> s.charAt(0))
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of("apple", "ape"), ctx);
	        Map grouped = (Map) result.value();
	        assertThrows(
	            UnsupportedOperationException.class,
	            () -> grouped.put('x', List.of("x"))
	        );
	    }
	    
	    @Test
	    void completed_only_should_explicitly_exclude_cancelled() {
	        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
	        results.put("task1", new Success<>("A"));
	        results.put("task2", new Failure<>(new RuntimeException("boom")));
	        results.put("task3", new Cancelled<>("cancelled"));
	        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
	                results,
	                List.of("task1", "task2", "task3")
	            );
	        List<?> collected = GatherCollectors.collectFromParallelResult(
	                parallel,
	                CollectionMode.COMPLETED_ONLY
	            );
	        assertEquals(2, collected.size());
	        assertFalse(
	            collected.stream().anyMatch(r ->
	                r instanceof Cancelled
	            )
	        );
	    }

	    @Test
	    @SuppressWarnings("unchecked")
	    void group_by_should_return_deeply_immutable_lists() {
	        GatherNode node = GatherNode.builder()
	            .groupBy((String s) -> s.charAt(0))
	            .build("gather");
	        NodeContext ctx = new NodeContext();
	        GatherNode.GatherResult result = node.process(List.of("apple", "ape"), ctx);
	        Map<?, ?> grouped = result.value();
	        List<String> groupA = (List<String>) grouped.get('a');
	        assertThrows(
	            UnsupportedOperationException.class,
	            () -> groupA.add("hacked")
	        );
	    }
}