package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Objects;

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

	    //@Test
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

	    //@Test
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

	    //@Test
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
	    //@Test
	    void branch_else_executes_when_no_match() {
	        Graph graph = GraphBuilder.named("branch-else")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("contains-a", s -> s.contains("a")).then("A")
	                    .orElse("ELSE")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("ELSE", new AppendNode("_ELSE"))
	            .connect("branch", "A")
	            .connect("branch", "ELSE")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result = executor.run(graph, "zzz", ctx);
	        assertEquals("zzz_ELSE", result.get("ELSE"));
	        assertNull(result.get("A"));
	    }
	    //@Test
	    void branch_throws_when_no_match_and_no_else() {
	        Graph graph = GraphBuilder.named("branch-fail")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("contains-a", s -> s.contains("a")).then("A")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .connect("branch", "A")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        assertThrows(
	            BranchNode.NoBranchMatchedException.class,
	            () -> executor.run(graph, "zzz", ctx)
	        );
	    }
	   // @Test
	    void branch_transform_applies_before_next_node() {
	        Graph graph = GraphBuilder.named("branch-transform")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when(
	                        "uppercase",
	                        s -> true,
	                        String::toUpperCase
	                    )
	                    .then("A")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_DONE"))
	            .connect("branch", "A")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result = executor.run(graph, "test", ctx);
	        assertEquals("TEST_DONE", result.get("A"));
	    }

	    //@Test
	    void branch_returns_routed_result() {
	        BranchNode<String> node = BranchNode.<String>builder()
	            .when("contains-a", s -> s.contains("a")).then("A")
	            .build("branch");
	        NodeContext ctx = new NodeContext();
	        Object result = node.process("apple", ctx);
	        assertInstanceOf(BranchNode.RoutedResult.class, result);
	        BranchNode.RoutedResult routed =
	            (BranchNode.RoutedResult) result;
	        assertEquals("A", routed.nextNode());
	        assertEquals("apple", routed.output());
	    }
	    @Test
	    void branch_handles_null_input_with_else() {
	        Graph graph = GraphBuilder.named("branch-null")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("non-null", Objects::nonNull).then("A")
	                    .orElse("ELSE")
	                    .build("branch")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("ELSE", new AppendNode("_ELSE"))
	            .connect("branch", "A")
	            .connect("branch", "ELSE")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result = executor.run(graph, null, ctx);
	        assertEquals("null_ELSE", result.get("ELSE"));
	    }
}