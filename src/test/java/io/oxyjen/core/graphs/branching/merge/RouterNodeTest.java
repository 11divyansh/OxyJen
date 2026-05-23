package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.oxyjen.graph.branching.RouterNode;

class UpperCaseNode implements NodePlugin<String, String> {
    public String process(String input, NodeContext ctx) {
        return input.toUpperCase();
    }
}

class AppendNode implements NodePlugin<String, String> {
    private final String suffix;
    AppendNode(String suffix) { this.suffix = suffix; }

    public String process(String input, NodeContext ctx) {
        return input + suffix;
    }
}

class CollectNode implements NodePlugin<String, String> {
    public String process(String input, NodeContext ctx) {
        ctx.set("result", input);
        return input;
    }
}
class RouterNodeTest {

	@Test
	void router_single_route() {
	    Graph graph = GraphBuilder.named("single-route")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("hasA", s -> s.contains("a"), "A")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .connect("router", "A")
	        .build();

	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "apple", ctx);
	    assertEquals("apple_A", result.get("A"));
	}
	
	@Test
	void router_multi_route_parallel() {
	    Graph graph = GraphBuilder.named("fanout")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("hasA", s -> s.contains("a"), "A")
	                .route("hasB", s -> s.contains("b"), "B")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .addNode("B", new AppendNode("_B"))
	        .connect("router", "A")
	        .connect("router", "B")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "ab", ctx);
	    assertEquals("ab_A", result.get("A"));
	    assertEquals("ab_B", result.get("B"));
	}
	
	@Test
	void router_no_route_fires() {
	    Graph graph = GraphBuilder.named("no-route")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("hasA", s -> s.contains("a"), "A")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .connect("router", "A")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "xyz", ctx);
	    assertTrue(result.isEmpty() || result.get("A") == null);
	}
	@Test
	void router_require_at_least_one() {
	    Graph graph = GraphBuilder.named("require-route")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("hasA", s -> s.contains("a"), "A")
	                .requireAtLeastOne()
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_A"))
	        .connect("router", "A")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    assertThrows(RuntimeException.class, () ->
	        executor.run(graph, "xyz", ctx)
	    );
	}
	@Test
	void router_with_transform() {
	    Graph graph = GraphBuilder.named("transform")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("upper", s -> true, s -> s.toUpperCase(), "A")
	                .build("router")
	        )
	        .addNode("A", new AppendNode("_DONE"))
	        .connect("router", "A")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "hello", ctx);
	    assertEquals("HELLO_DONE", result.get("A"));
	}
	class FailingNode implements NodePlugin<String, String> {
	    public String process(String input, NodeContext ctx) {
	        throw new RuntimeException("boom");
	    }
	}

	@Test
	void router_with_failure_collect_mode() {
	    ExecutionRuntime runtime = ExecutionRuntime.defaultRuntime()
	        .builder().failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS).build();
	    ParallelExecutor executor = new ParallelExecutor(runtime);
	    Graph graph = GraphBuilder.named("failure")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("all", s -> true, "fail")
	                .build("router")
	        )
	        .addNode("fail", new FailingNode())
	        .connect("router", "fail")
	        .build();
	    NodeContext ctx = new NodeContext();
	    Map<String, Object> result = executor.run(graph, "input", ctx);
	    assertTrue(result.get("fail") instanceof ParallelExecutor.NodeFailure);
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
	void router_parallel_execution_time() {
	    Graph graph = GraphBuilder.named("parallel-check")
	        .addNode("router",
	            RouterNode.<String>builder()
	                .route("r1", s -> true, "A")
	                .route("r2", s -> true, "B")
	                .build("router")
	        )
	        .addNode("A", new SlowNode("A"))
	        .addNode("B", new SlowNode("B"))
	        .connect("router", "A")
	        .connect("router", "B")
	        .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    NodeContext ctx = new NodeContext();
	    long start = System.currentTimeMillis();
	    executor.run(graph, "test", ctx);
	    long time = System.currentTimeMillis() - start;
	    assertTrue(time < 900);
	}
}