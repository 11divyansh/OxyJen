package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;

class RouterMergeTest {

	//@Test
	void router_merge_success_all() {
	    MergeNode merge = new MergeNode.Builder()
	        .expect("A", "B")
	        .build("merge");

	    Graph graph = GraphBuilder.named("merge-success")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("r1", s -> true, "A")
	                .route("r2", s -> true, "B")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .addNode("B", new AppendNode("_B"))
	        .addNode("merge", merge)
	        .connect("router", "A")
	        .connect("router", "B")
	        .connect("A", "merge")
	        .connect("B", "merge")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "test", ctx);
	    MergeNode.MergeResult mergeResult =
	        (MergeNode.MergeResult) result.get("merge");
	    assertEquals("test_A", mergeResult.get("A"));
	    assertEquals("test_B", mergeResult.get("B"));
	    assertFalse(mergeResult.hasErrors());
	}
	class FailingNode implements NodePlugin<String, String> {
	    public String process(String input, NodeContext ctx) {
	        throw new RuntimeException("boom");
	    }
	}
	//@Test
	void router_merge_partial_failure_collect() {
	    MergeNode merge = new MergeNode.Builder()
	        .expect("A", "B")
	        .build("merge");

	    ExecutionRuntime runtime = ExecutionRuntime.defaultRuntime()
	        .builder()
	        .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	        .build();

	    Graph graph = GraphBuilder.named("merge-partial")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("r1", s -> true, "A")
	                .route("r2", s -> true, "B")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .addNode("B", new FailingNode())
	        .addNode("merge", merge)
	        .connect("router", "A")
	        .connect("router", "B")
	        .connect("A", "merge")
	        .connectOnFailure("B", "merge")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor(runtime);
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "test", ctx);
	    MergeNode.MergeResult mergeResult =
	        (MergeNode.MergeResult) result.get("merge");
	    assertEquals("test_A", mergeResult.get("A"));
	    assertTrue(mergeResult.hasErrors());
	    assertTrue(mergeResult.getErrors().containsKey("B"));
	}

	class SlowNode implements NodePlugin<String, String> {
	    private final String name;
	    SlowNode(String name) { this.name = name; }

	    public String process(String input, NodeContext ctx) {
	        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
	        return input + "_" + name;
	    }
	}
	@Test
	void router_merge_first_wins() {
	    MergeNode merge = new MergeNode.Builder()
	        .expect("A", "B")
	        .strategy(MergeNode.MergeStrategy.FIRST_WINS)
	        .build("merge");

	    Graph graph = GraphBuilder.named("merge-first")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("r1", s -> true, "A")
	                .route("r2", s -> true, "B")
	                .build("router")
	        )
	        .addNode("A", new SlowNode("A"))
	        .addNode("B", new SlowNode("B"))
	        .addNode("merge", merge)
	        .connect("router", "A")
	        .connect("router", "B")
	        .connect("A", "merge")
	        .connect("B", "merge")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "test", ctx);
	    Object mergeResult = result.get("merge");
	    assertNotNull(mergeResult);
	}
}