package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;

class ParallelNodeExecutionTest {
	@Test
	void shouldAddMultipleNodesUsingAddParallelNodes() {
	    Graph graph = GraphBuilder.named("parallel-nodes")
	            .addParallelNodes(
	                    "A", (NodePlugin<String, String>) (input, ctx) -> input + "A",
	                    "B", (NodePlugin<String, String>) (input, ctx) -> input + "B"
	            )
	            .build();
	    assertEquals(2, graph.getNodes().size());
	}
	@Test
	void shouldThrowIfOddNumberOfArguments() {
	    GraphBuilder builder = GraphBuilder.named("invalid");
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addParallelNodes(
	                "A", (NodePlugin<String, String>) (i, c) -> i,
	                "B" // missing node pair
	        );
	    });
	}
	@Test
	void shouldThrowIfNameIsNotString() {
	    GraphBuilder builder = GraphBuilder.named("invalid");
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addParallelNodes(
	                123, (NodePlugin<String, String>) (i, c) -> i
	        );
	    });
	}
	@Test
	void shouldThrowIfNodeIsNotNodePlugin() {
	    GraphBuilder builder = GraphBuilder.named("invalid");
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addParallelNodes(
	                "A", "not-a-node"
	        );
	    });
	}
	@Test
	void shouldThrowIfDuplicateNamesInParallelNodes() {
	    GraphBuilder builder = GraphBuilder.named("dup");
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addParallelNodes(
	                "A", (NodePlugin<String, String>) (i, c) -> i,
	                "A", (NodePlugin<String, String>) (i, c) -> i
	        );
	    });
	}
	@Test
	void shouldThrowIfParallelNodeDuplicatesExistingNode() {
	    GraphBuilder builder = GraphBuilder.named("dup-existing")
	            .addNode("A", (NodePlugin<String, String>) (i, c) -> i);
	    assertThrows(IllegalArgumentException.class, () -> {
	        builder.addParallelNodes(
	                "A", (NodePlugin<String, String>) (i, c) -> i
	        );
	    });
	}
	@Test
	void shouldExecuteParallelNodesIndependently() {
	    Graph graph = GraphBuilder.named("parallel-exec")
	            .addNode("start", (NodePlugin<String, String>) (i, c) -> i)
	            .addParallelNodes(
	                    "A", (NodePlugin<String, String>) (i, c) -> i + "-A",
	                    "B", (NodePlugin<String, String>) (i, c) -> i + "-B",
	                    "C", (NodePlugin<String, String>) (i, c) -> i + "-C"
	            )
	            .connect("start", "A")
	            .connect("start", "B")
	            .connect("start", "C")
	            .build();
	    Map<String, Object> result =
	            new ParallelExecutor().run(graph, "data", new NodeContext());
	    assertEquals(3, result.size());
	    assertTrue(result.values().contains("data-A"));
	    assertTrue(result.values().contains("data-B"));
	    assertTrue(result.values().contains("data-C"));
	}
	@Test
	void shouldRunParallelNodesConcurrently() {
	    AtomicInteger concurrent = new AtomicInteger(0);
	    AtomicInteger maxSeen = new AtomicInteger(0);
	    NodePlugin<String, String> slowNode = (input, ctx) -> {
	        int now = concurrent.incrementAndGet();
	        maxSeen.updateAndGet(prev -> Math.max(prev, now));
	        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
	        concurrent.decrementAndGet();
	        return input;
	    };
	    Graph graph = GraphBuilder.named("parallel-concurrency")
	            .addParallelNodes(
	                    "A", slowNode,
	                    "B", slowNode,
	                    "C", slowNode
	            )
	            .build();
	    new ParallelExecutor().run(graph, "x", new NodeContext());
	    assertTrue(maxSeen.get() > 1); // proves parallel execution
	}
	@Test
	void shouldAllowEmptyParallelNodesCall() {
	    GraphBuilder builder = GraphBuilder.named("empty");
	    assertThrows(IllegalStateException.class, () -> {
	        builder.addParallelNodes().build();
	    });
	}
}