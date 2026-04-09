package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
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
	    assertEquals("data-A", result.get("AppendNode"));
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
}