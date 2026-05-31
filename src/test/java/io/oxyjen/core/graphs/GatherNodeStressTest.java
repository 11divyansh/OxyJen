package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.ParallelNode;
import io.oxyjen.graph.concurrency.ParallelNode.Builder;

class GatherNodeStressTest {

	//@Test
	void large_parallel_node_gather_should_collect_all_results() {
	    Builder<Integer, Integer> parallel = ParallelNode.<Integer, Integer>builder();
	    int taskCount = 100;
	    for (int i = 0; i < taskCount; i++) {
	        int value = i;
	        parallel.task("task-" + i, input -> value);
	    }
	    ParallelNode<Integer, Integer> node = parallel.build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<Integer> result = node.process(0, ctx);
	    GatherNode gather = GatherNode.builder()
	        .collectMode(CollectionMode.SUCCESS_ONLY)
	        .aggregate(GatherNode.Aggregation.COUNT)
	        .build("gather");
	    GatherNode.GatherResult gathered = gather.process(result, ctx);
	    assertEquals(taskCount, ((Number) gathered.value()).longValue());
	    assertEquals(taskCount, result.successCount());
	    assertEquals(0, result.failureCount());
	}
	//@Test
	void completion_order_should_contain_every_task_once() {
	    Builder<Integer, Integer> parallel = ParallelNode.<Integer, Integer>builder();
	    int taskCount = 50;
	    for (int i = 0; i < taskCount; i++) {
	        int value = i;
	        parallel.task(
	            "task-" + i,
	            input -> { try {
					Thread.sleep(value % 5);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} return value; }
	        );
	    }
	    ParallelNode<Integer, Integer> node = parallel.build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<Integer> result = node.process(0, ctx);
	    List<String> order = result.completionOrder();
	    assertEquals(taskCount, order.size());
	    assertEquals(taskCount, new HashSet<>(order).size());
	}
	
	@Test
	void repeated_gather_runs_should_be_deterministic() {
	    GatherNode node = GatherNode.builder()
	        .filter((Integer i) -> i % 2 == 0)
	        .sortBy(Integer::compareTo)
	        .limit(3)
	        .aggregate(GatherNode.Aggregation.LIST)
	        .build("gather");
	    NodeContext ctx = new NodeContext();
	    List<Integer> input = List.of(8, 1, 6, 2, 4, 3);
	    GatherNode.GatherResult first = node.process(input, ctx);
	    GatherNode.GatherResult second = node.process(input, ctx);
	    GatherNode.GatherResult third = node.process(input, ctx);
	    assertEquals(Objects.toString(first.value()),Objects.toString(second.value()));
	    assertEquals(Objects.toString(second.value()), Objects.toString(third.value()));
	}
	
	@Test
	void parallel_node_should_preserve_all_successes_under_contention() {

	    Builder<Integer, Integer> parallel = ParallelNode.<Integer, Integer>builder();
	    int taskCount = 100;
	    for (int i = 0; i < taskCount; i++) {
	        int value = i;
	        parallel.task(
	            "task-" + i,
	            input -> {
	                try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	                return value;
	            }
	        );
	    }
	    ParallelNode<Integer, Integer> node = parallel.build("parallel");
	    NodeContext ctx = new NodeContext();
	    ctx.setRuntime(ExecutionRuntime.defaultRuntime());
	    ParallelNode.ParallelResult<Integer> result =node.process(0, ctx);
	    assertEquals(taskCount, result.successCount());
	    assertEquals(taskCount, result.allOutputs().size());
	}
}