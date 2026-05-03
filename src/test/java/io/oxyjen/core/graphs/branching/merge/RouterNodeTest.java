package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
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
}
