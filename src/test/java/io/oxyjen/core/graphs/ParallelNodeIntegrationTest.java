package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.execution.result.Cancelled;
import io.oxyjen.execution.result.Failure;
import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;

class ParallelNodeIntegrationTest {

	@Test
	void parallel_success_only_gather_count() {
	    ParallelNode<String, String> parallel = ParallelNode.<String, String>builder()
	    		.task("a", s -> s + "_A")
	            .task("b", s -> s + "_B")
	            .task("c", s -> s + "_C")
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<String> result = parallel.process("test", ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.SUCCESS_ONLY)
	        .aggregate(GatherNode.Aggregation.COUNT)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    assertEquals(3L, ((Number) gathered.value()).longValue());
	}
	
	@Test
	void parallel_all_results_gather_should_preserve_all_task_results() {
	    ParallelNode<String, String> parallel = ParallelNode.<String, String>builder()
	            .task("success", s -> s)
	            .task("failure", s -> {throw new RuntimeException("boom");})
	            .continueOnError()
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<String> result = parallel.process("test", ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.ALL_RESULTS)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    List<?> items = gathered.items();
	    assertEquals(2, items.size());
	    assertTrue(items.stream().anyMatch(r -> r instanceof Success));
	    assertTrue(items.stream().anyMatch(r -> r instanceof Failure));
	}
	
	@Test
	void failures_only_should_collect_only_failures() {
	    ParallelNode<String, String> parallel = ParallelNode.<String, String>builder()
	            .task("ok", s -> s)
	            .task("bad", s -> {
	                throw new RuntimeException("boom");
	            })
	            .continueOnError()
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<String> result = parallel.process("test", ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.FAILURES_ONLY)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    assertEquals(1, gathered.items().size());
	    assertInstanceOf(Failure.class, gathered.items().get(0));
	}
	
	@Test
	void completed_only_should_exclude_cancelled_results() {
	    Map<String, TaskResult<String>> results = new LinkedHashMap<>();
	    results.put("success", new Success<>("ok"));
	    results.put("failure", new Failure<>(new RuntimeException()));
	    results.put("cancelled", new Cancelled<>("cancelled"));
	    ParallelNode.ParallelResult<String> parallel = ParallelNode.ParallelResult.of(
	            results,
	            List.of("success", "failure", "cancelled")
	        );
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.COMPLETED_ONLY)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(parallel, new NodeContext());
	    assertEquals(2, gathered.items().size());
	    assertFalse(gathered.items().stream().anyMatch(r -> r instanceof Cancelled));
	}
	
	@Test
	void continue_on_error_should_preserve_successful_values() {
	    ParallelNode<String, String> parallel = ParallelNode.<String, String>builder()
	            .task("a", s -> s + "_A")
	            .task("b", s -> {
	                throw new RuntimeException("boom");
	            })
	            .task("c", s -> s + "_C")
	            .continueOnError()
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<String> result = parallel.process("test", ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.SUCCESS_ONLY)
	        .aggregate(GatherNode.Aggregation.COUNT)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    assertEquals(2L, ((Number) gathered.value()).longValue());
	}
	@Test
	void timeout_should_propagate_cancelled_results() {
	    ParallelNode<String, String> parallel = ParallelNode.<String, String>builder()
	            .task("slow", s -> {
	                try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	                return s;
	            })
	            .timeout(50, TimeUnit.MILLISECONDS)
	            .continueOnError()
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ExecutionRuntime runtime = ExecutionRuntime.defaultRuntime();
	    ctx.setRuntime(runtime);
	    ParallelNode.ParallelResult<String> result = parallel.process("test", ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.ALL_RESULTS)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    assertTrue(gathered.items().stream().anyMatch(r -> r instanceof Cancelled));
	}
}