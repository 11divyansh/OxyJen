package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
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
import io.oxyjen.graph.concurrency.MapNode;
import io.oxyjen.graph.concurrency.ParallelNode;
import io.oxyjen.graph.concurrency.ParallelNode.ParallelResult;

class GatherCollectorsTest {

	@Test
	void shouldCollectOnlySuccesses() {
	    Map<String, TaskResult<String>> results = new LinkedHashMap<>();
	    results.put("a", new Success<>("ok"));
	    results.put("b", new Failure<>(new RuntimeException()));
	    results.put("c", new Cancelled<>("timeout"));
	    ParallelResult<String> parallel =
	            ParallelResult.of(
	                    results,
	                    List.of("a", "b", "c")
	            );
	    List<?> collected =
	            GatherCollectors.collectFromParallelResult(
	                    parallel,
	                    CollectionMode.SUCCESS_ONLY
	            );
	    assertEquals(List.of("ok"), collected);
	}
	@Test
    void success_only_should_return_raw_values_from_parallel_result() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("A"));
        results.put("b", new Failure<>(new RuntimeException("boom")));
        results.put("c", new Cancelled<>("cancelled"));
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                    results,
                    List.of("task1", "task2", "task3")
                );
        List<?> collected = GatherCollectors.collectFromParallelResult(
                parallel,
                CollectionMode.SUCCESS_ONLY
            );
        assertEquals(1, collected.size());
        assertEquals("A", collected.get(0));
    }

    @Test
    void failures_only_should_return_only_failure_results() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("A"));
        results.put("b", new Failure<>(new RuntimeException("boom")));
        results.put("c", new Cancelled<>("cancelled"));
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                    results,
                    List.of("task1", "task2", "task3")
                );

        List<?> collected = GatherCollectors.collectFromParallelResult(
                parallel,
                CollectionMode.FAILURES_ONLY
            );
        assertEquals(1, collected.size());
        assertInstanceOf(Failure.class, collected.get(0));
    }

    @Test
    void all_results_should_preserve_all_task_results() {

        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("A"));
        results.put("b", new Failure<>(new RuntimeException("boom")));
        results.put("c", new Cancelled<>("cancelled"));

        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                    results,
                    List.of("task1", "task2", "task3")
                );

        List<?> collected = GatherCollectors.collectFromParallelResult(
                parallel,
                CollectionMode.ALL_RESULTS
            );
        assertEquals(3, collected.size());
        assertInstanceOf(Success.class, collected.get(0));
        assertInstanceOf(Failure.class, collected.get(1));
        assertInstanceOf(Cancelled.class, collected.get(2));
    }

    @Test
    void completed_only_should_exclude_cancelled_results() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        results.put("a", new Success<>("A"));
        results.put("b", new Failure<>(new RuntimeException("boom")));
        results.put("c", new Cancelled<>("cancelled"));
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                    results,
                    List.of("task1", "task2", "task3")
                );
        List<?> collected = GatherCollectors.collectFromParallelResult(
                parallel,
                CollectionMode.COMPLETED_ONLY
            );
        assertEquals(2, collected.size());
        assertTrue(
            collected.stream().allMatch(r ->
                r instanceof Success || r instanceof Failure
            )
        );
    }

    @Test
    void empty_parallel_result_should_return_empty_collection() {
        Map<String, TaskResult<String>> results = new LinkedHashMap<>();
        ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
                    Collections.emptyMap(),
                    Collections.emptyList()
                );
        List<?> collected = GatherCollectors.collectFromParallelResult(
                parallel,
                CollectionMode.ALL_RESULTS
            );
        assertTrue(collected.isEmpty());
    }

    @Test
    void empty_map_result_should_return_empty_collection() {
    	MapNode<String, String> mapNode =
    		    MapNode.<String, String>builder()
    		        .mapWith(s -> s + "_mapped")
    		        .build("map");
    	NodeContext ctx = new NodeContext();
    	MapNode.MapResult<String> mapResult = mapNode.process(Collections.emptyList(), ctx);
        List<?> collected =
            GatherCollectors.collectFromMapResult(
                mapResult,
                CollectionMode.ALL_RESULTS
            );
        assertTrue(collected.isEmpty());
    }

    @Test
    void iterable_normalization_should_return_snapshot_copy() {
        List<String> source = new ArrayList<>(List.of("A", "B"));
        List<?> collected = GatherCollectors.collectFromIterable(source);
        source.add("C");
        assertEquals(List.of("A", "B"), collected);
    }

    @Test
    void single_value_should_be_wrapped_in_list() {

        GatherNode node = GatherNode.builder().build("gather");
        NodeContext ctx = new NodeContext();
        GatherNode.GatherResult result = node.process("hello", ctx);
        assertEquals(List.of("hello"), result.items());
    }
}