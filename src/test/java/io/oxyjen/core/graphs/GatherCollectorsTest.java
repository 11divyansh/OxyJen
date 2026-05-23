package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.gather.GatherCollectors;
import io.oxyjen.execution.result.Cancelled;
import io.oxyjen.execution.result.Failure;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
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
}