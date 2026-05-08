package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.BranchNode;

class BranchNodeTest {

	 static class AppendNode implements NodePlugin<String, String> {
	        private final String suffix;

	        AppendNode(String suffix) {
	            this.suffix = suffix;
	        }

	        @Override
	        public String process(String input, NodeContext context) {
	            return input + suffix;
	        }
	    }

	    @Test
	    void branch_first_match_executes() {
	        Graph graph = GraphBuilder.named("branch-first")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("contains-a", s -> s.contains("a"))
	                    .then("A")
	                    .when("contains-b", s -> s.contains("b"))
	                    .then("B")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .connect("branch", "A")
	            .connect("branch", "B")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result = executor.run(graph, "apple", ctx);
	        assertEquals("apple_A", result.get("A"));
	        assertNull(result.get("B"));
	    }

	    @Test
	    void branch_second_match_executes_when_first_fails() {
	        Graph graph = GraphBuilder.named("branch-second")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("contains-z", s -> s.contains("z"))
	                    .then("A")
	                    .when("contains-b", s -> s.contains("b"))
	                    .then("B")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .connect("branch", "A")
	            .connect("branch", "B")
	            .build();

	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();

	        Map<String, Object> result = executor.run(graph, "banana", ctx);

	        assertEquals("banana_B", result.get("B"));
	        assertNull(result.get("A"));
	    }

	    @Test
	    void branch_first_match_wins_when_multiple_match() {
	        Graph graph = GraphBuilder.named("branch-priority")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("broad", s -> true)
	                    .then("A")
	                    .when("specific", s -> true)
	                    .then("B")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .connect("branch", "A")
	            .connect("branch", "B")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result = executor.run(graph, "test", ctx);
	        assertEquals("test_A", result.get("A"));
	        assertNull(result.get("B"));
	    }
}