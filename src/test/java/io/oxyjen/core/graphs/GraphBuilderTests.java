package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.FailureMode;
import io.oxyjen.graph.ParallelExecutor;

class GraphBuilderTests {

	@Test
	void shouldBuildSimpleLinearGraph() {
	    Graph graph = GraphBuilder.named("simple")
	            .addNode("input", new Nodes.InputNode())
	            .addNode("upper", new Nodes.UppercaseNode())
	            .connect("input", "upper")
	            .build();
	    assertEquals(2, graph.getNodes().size());
	    assertEquals(1, graph.getRootNodes().size());
	}
	@Test
	void shouldThrowOnDuplicateNodeNames() {
	    GraphBuilder builder = GraphBuilder.named("dup");
	    builder.addNode("A", new Nodes.InputNode());
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addNode("A", new Nodes.InputNode());
	    });
	}
	@Test
	void shouldThrowIfConnectingUnknownNode() {
	    GraphBuilder builder = GraphBuilder.named("invalid")
	            .addNode("A", new Nodes.InputNode());
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.connect("A", "B");
	    });
	}
	@Test
	void shouldRouteBasedOnCondition_debug() {
	    Graph graph = GraphBuilder.named("routing")
	            .addNode("start", new Nodes.InputNode())
	            .addNode("A", new Nodes.AppendNode("-A"))
	            .addNode("B", new Nodes.AppendNode("-B"))
	            .route("start", ctx -> {
	                Object val = ctx.getMetadata("route");
	                System.out.println("ROUTER VALUE = " + val);
	                return (String) val;
	            })
	                .to("A", "A")
	                .to("B", "B")
	                .end()
	            .build();

	    NodeContext ctx = new NodeContext();
	    ctx.setMetadata("route", "A");

	    Map<String, Object> result = new ParallelExecutor().run(graph, "data", ctx);

	    System.out.println("RESULT = " + result);
	    assertEquals("data-A", result.get("A"));
	}
	@Test
	void shouldLoopUntilConditionFails() {
	    Nodes.CountingNode node = new Nodes.CountingNode();

	    Graph graph = GraphBuilder.named("loop")
	            .addNode("counter", node)
	            .addNode("end", (NodePlugin<Integer, Integer>) (i, ctx) -> i)
	            .connect("counter", "end")
	            .repeat("counter")
	                .whileCondition((out, ctx) -> (int) out < 3)
	                .max(5)
	                .build()
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    Map<String, Object> result = executor.run(graph, 1, new NodeContext());
	    assertEquals(3, result.values().iterator().next());
	}
	@Test
	void shouldFailIfLoopWithoutCondition() {
	    GraphBuilder builder = GraphBuilder.named("bad-loop")
	            .addNode("A", new Nodes.InputNode());
	    assertThrows(IllegalStateException.class, () -> {
	        builder.repeat("A").build();
	    });
	}
	@Test
	void shouldExecuteLinearGraph() {
	    Graph graph = GraphBuilder.named("linear")
	            .addNode("A", new Nodes.InputNode())
	            .addNode("B", new Nodes.UppercaseNode())
	            .connect("A", "B")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    String result = executor.runSingle(graph, "hello", new NodeContext());
	    assertEquals("HELLO", result);
	}
	@Test
	void shouldExecuteParallelBranches() {
	    Graph graph = GraphBuilder.named("parallel")
	            .addNode("start", new Nodes.InputNode())
	            .addNode("A", new Nodes.AppendNode("-A"))
	            .addNode("B", new Nodes.AppendNode("-B"))
	            .connect("start", "A")
	            .connect("start", "B")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    Map<String, Object> result = executor.run(graph, "data", new NodeContext());
	    assertEquals(2, result.size());
	}
	@Test
	void shouldWaitForAllUpstreamsBeforeExecution() {
	    AtomicInteger executions = new AtomicInteger();
	    NodePlugin<String, String> joinNode = new NodePlugin<>() {
	        @Override
	        public String process(String input, NodeContext context) {
	            executions.incrementAndGet();
	            return input;
	        }
	    };
	    Graph graph = GraphBuilder.named("fanin")
	            .addNode("start", new Nodes.InputNode())
	            .addNode("A", new Nodes.AppendNode("-A"))
	            .addNode("B", new Nodes.AppendNode("-B"))
	            .addNode("join", joinNode)
	            .connect("start", "A")
	            .connect("start", "B")
	            .connect("A", "join")
	            .connect("B", "join")
	            .build();

	    new ParallelExecutor().run(graph, "data", new NodeContext());
	    assertEquals(2, executions.get()); // node execute once per incoming edge
	    // for real join behavior MergeNode will be introduced
	}
	@Test
	void shouldFailFastOnError() {
	    Graph graph = GraphBuilder.named("failfast")
	            .addNode("A", new Nodes.InputNode())
	            .addNode("B", new Nodes.FailingNode())
	            .connect("A", "B")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    assertThrows(RuntimeException.class, () -> {
	        executor.run(graph, "data", new NodeContext());
	    });
	}
	@Test
	void shouldContinueOnError() {
	    Graph graph = GraphBuilder.named("continue")
	            .addNode("A", new Nodes.InputNode())
	            .addNode("B", new Nodes.FailingNode())
	            .addNode("C", new Nodes.AppendNode("-C"))
	            .connect("A", "B")
	            .connect("A", "C")
	            .build();

	    ParallelExecutor executor = new ParallelExecutor(
	            ForkJoinPool.commonPool(),
	            FailureMode.CONTINUE_ON_ERROR
	    );
	    Map<String, Object> result = executor.run(graph, "data", new NodeContext());
	    assertTrue(result.values().contains("data-C"));
	}
	@Test
	void shouldPropagateContextAcrossNodes() {
	    NodePlugin<String, String> writer = new NodePlugin<>() {
	        @Override
	        public String process(String input, NodeContext context) {
	            context.setMetadata("key", "value");
	            return input;
	        }
	    };
	    NodePlugin<String, String> reader = new NodePlugin<>() {
	        @Override
	        public String process(String input, NodeContext context) {
	            return (String) context.getMetadata("key");
	        }
	    };
	    Graph graph = GraphBuilder.named("context")
	            .addNode("A", writer)
	            .addNode("B", reader)
	            .connect("A", "B")
	            .build();
	    String result = new ParallelExecutor().runSingle(graph, "x", new NodeContext());
	    assertEquals("value", result);
	}
}