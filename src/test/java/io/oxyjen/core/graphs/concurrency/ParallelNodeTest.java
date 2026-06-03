package io.oxyjen.core.graphs.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.graph.concurrency.ParallelNode;
import io.oxyjen.graph.concurrency.ParallelNode.ParallelResult;

class ParallelNodeTest {

	@Test
	void should_collect_all_successful_results() {
	    ParallelNode<String, String> node =ParallelNode.<String, String>builder()
	            .task("upper", String::toUpperCase)
	            .task("append", s -> s + "_done")
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ParallelResult<String> result = node.process("hello", ctx);
	    assertEquals(2, result.successCount());
	    assertEquals("HELLO",result.getOrDefault("upper", null));
	    assertEquals("hello_done",result.getOrDefault("append", null));
	}
	
	@Test
	void should_capture_failures() {
	    ParallelNode<String, String> node = ParallelNode.<String, String>builder()
	            .task("good", s -> s)
	            .task("bad", s -> {
	                throw new RuntimeException("boom");
	            })
	            .continueOnError()
	            .build("parallel");
	    ParallelResult<String> result =node.process("x", new NodeContext());
	    assertEquals(1, result.successCount());
	    assertEquals(1, result.failureCount());
	    assertTrue(result.failed("bad"));
	}
	
	@Test
	void should_preserve_completion_order() {
	    ParallelNode<String, String> node = ParallelNode.<String, String>builder()
	            .task("slow", s -> {
	                try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	                return s;
	            })
	            .task("fast", s -> s)
	            .build("parallel");
	    ParallelResult<String> result = node.process("x", new NodeContext());
	    assertEquals(2,result.completionOrder().size());
	}
	
	@Test
	void should_reject_duplicate_task_names() {
	    assertThrows(
	        IllegalStateException.class,
	        () -> ParallelNode.<String, String>builder()
	            .task("dup", s -> s)
	            .task("dup", s -> s)
	            .build("parallel")
	    );
	}
	
	@Test
	void should_timeout_tasks() {
	    ParallelNode<String, String> node = ParallelNode.<String, String>builder()
	            .task("slow", s -> {
	                try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
	                return s;
	            })
	            .timeout(10, TimeUnit.MILLISECONDS)
	            .continueOnError()
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ExecutionRuntime runtime = ExecutionRuntime.defaultRuntime();
	    ctx.setRuntime(runtime);
	    ParallelResult<String> result = node.process("x", ctx);
	    assertTrue(result.cancelledCount() > 0);
	}
	
	@Test
	void should_run_sequentially_without_runtime() {
	    ParallelNode<String, String> node = ParallelNode.<String, String>builder()
	            .task("a", String::toUpperCase)
	            .task("b", s -> s + "_x")
	            .build("parallel");
	    NodeContext ctx = new NodeContext();
	    ParallelResult<String> result = node.process("hello", ctx);
	    assertEquals(2, result.successCount());
	}
}