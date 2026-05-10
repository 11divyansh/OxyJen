package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.BranchNode;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.MergeNode.MergeResult;
import io.oxyjen.graph.branching.RouterNode;

class BranchRouterMergeTest {

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

	    static class SlowNode implements NodePlugin<String, String> {
	        private final String name;
	        private final long delay;

	        SlowNode(String name, long delay) {
	            this.name = name;
	            this.delay = delay;
	        }

	        @Override
	        public String process(String input, NodeContext context) {
	            try {
	                Thread.sleep(delay);
	            } catch (InterruptedException ignored) {}

	            return input + "_" + name;
	        }
	    }

	    static class FailingNode implements NodePlugin<String, String> {
	        @Override
	        public String process(String input, NodeContext context) {
	            throw new RuntimeException("boom");
	        }
	    }

	    //@Test
	    void branch_router_merge_collect_all() {
	        MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .strategy(MergeNode.MergeStrategy.COLLECT_ALL)
	            .build("merge");

	        Graph graph = GraphBuilder.named("branch-router-merge")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("short", s -> s.length() < 10)
	                    .then("router")
	                    .orElse("fallback")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("toA", s -> true, "A")
	                    .route("toB", s -> true, "B")
	                    .build("router")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .addNode("merge", merge)
	            .connect("branch", "router")
	            .connect("router", "A")
	            .connect("router", "B")
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx = new NodeContext();
	        Map<String, Object> result =
	            executor.run(graph, "test", ctx);
	        MergeResult mergeResult =
	            (MergeResult) result.get("merge");
	        assertNotNull(mergeResult);
	        Map<?, ?> merged =
	            (Map<?, ?>) mergeResult.getMerged();
	        assertEquals("test_A", merged.get("A"));
	        assertEquals("test_B", merged.get("B"));
	    }

	    //@Test
	    void branch_router_merge_first_wins() {
	        MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .strategy(MergeNode.MergeStrategy.FIRST_WINS)
	            .build("merge");

	        Graph graph = GraphBuilder.named("first-wins")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("go", s -> true)
	                    .then("router")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("fast", s -> true, "A")
	                    .route("slow", s -> true, "B")
	                    .build("router")
	            )
	            .addNode("A", new SlowNode("A", 100))
	            .addNode("B", new SlowNode("B", 500))
	            .addNode("merge", merge)
	            .connect("branch", "router")
	            .connect("router", "A")
	            .connect("router", "B")
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        Map<String, Object> result =
	            executor.run(graph, "input", new NodeContext());
	        MergeResult mergeResult =
	            (MergeResult) result.get("merge");
	        assertEquals("input_A", mergeResult.getMerged());
	    }

	    //@Test
	    void branch_router_merge_list_strategy() {
	        MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .strategy(MergeNode.MergeStrategy.LIST)
	            .build("merge");

	        Graph graph = GraphBuilder.named("list-strategy")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("go", s -> true)
	                    .then("router")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("r1", s -> true, "A")
	                    .route("r2", s -> true, "B")
	                    .build("router")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .addNode("merge", merge)
	            .connect("branch", "router")
	            .connect("router", "A")
	            .connect("router", "B")
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .build();

	        ParallelExecutor executor = new ParallelExecutor();
	        Map<String, Object> result = executor.run(graph, "test", new NodeContext());
	        MergeResult mergeResult = (MergeResult) result.get("merge");
	        List<?> merged = (List<?>) mergeResult.getMerged();
	        assertEquals(2, merged.size());
	        assertTrue(merged.contains("test_A"));
	        assertTrue(merged.contains("test_B"));
	    }
	    //@Test
	    void branch_else_path_skips_router_and_merge() {
	        Graph graph = GraphBuilder.named("else-path")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("contains-a", s -> s.contains("a"))
	                    .then("router")
	                    .orElse("fallback")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("r1", s -> true, "A")
	                    .build("router")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("fallback", new AppendNode("_FALLBACK"))
	            .connect("branch", "router")
	            .connect("branch", "fallback")
	            .connect("router", "A")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        Map<String, Object> result = executor.run(graph, "zzz", new NodeContext());
	        assertEquals(
	            "zzz_FALLBACK",
	            result.get("fallback")
	        );
	        assertNull(result.get("A"));
	    }
	    //@Test
	    void branch_router_merge_timeout() {
	        MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .timeout(200, TimeUnit.MILLISECONDS)
	            .build("merge");
	        Graph graph = GraphBuilder.named("timeout")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("go", s -> true)
	                    .then("router")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("onlyA", s -> true, "A")
	                    .build("router")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .addNode("B", new AppendNode("_B"))
	            .addNode("merge", merge)
	            .connect("branch", "router")
	            .connect("router", "A")
	            .connect("A", "merge")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        assertThrows(
	            MergeNode.MergeTimeoutException.class,
	            () -> executor.run(graph, "test", new NodeContext())
	        );
	    }
	    @Test
	    void branch_router_metrics_increment() {
	        Graph graph = GraphBuilder.named("metrics")
	            .addNode("branch",
	                BranchNode.<String>builder()
	                    .when("short", s -> s.length() < 10)
	                    .then("router")
	                    .build("branch")
	            )
	            .addNode("router",
	                RouterNode.<String>builder()
	                    .route("A-route", s -> true, "A")
	                    .build("router")
	            )
	            .addNode("A", new AppendNode("_A"))
	            .connect("branch", "router")
	            .connect("router", "A")
	            .build();
	        ParallelExecutor executor = new ParallelExecutor();
	        NodeContext ctx1 = new NodeContext();
	        NodeContext ctx2 = new NodeContext();
	        executor.run(graph, "apple", ctx1);
	        executor.run(graph, "banana", ctx2);
	        long metric = ctx1.getRuntime()
	            .getMetrics()
	            .get("branch.branch.short.count");
	        assertEquals(2, metric);
	    }
}