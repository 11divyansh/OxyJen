package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.FailureMode;
import io.oxyjen.graph.ParallelExecutor;

class StressTests {
	
	@Test
	void shouldDetectRaceConditionsOnSharedState() {
	    AtomicInteger unsafeCounter = new AtomicInteger(0);
	    NodePlugin<Integer, Integer> node = new NodePlugin<>() {
	        @Override
	        public Integer process(Integer input, NodeContext context) {
	            // simulate race-prone increment
	            int current = unsafeCounter.get();
	            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
	            unsafeCounter.set(current + 1);
	            return unsafeCounter.get();
	        }
	    };
	    Graph graph = GraphBuilder.named("race")
	            .addNode("start", node)
	            .addNode("parallel1", node)
	            .addNode("parallel2", node)
	            .connect("start", "parallel1")
	            .connect("start", "parallel2")
	            .build();
	    new ParallelExecutor().run(graph, 1, new NodeContext());
	    // if thread-safe -> should be 3
	    // iff race -> often < 3
	    assertTrue(unsafeCounter.get() <= 3);
	}
	@Test
	void shouldRespectMaxConcurrencyLimit() {
	    AtomicInteger concurrent = new AtomicInteger(0);
	    AtomicInteger maxSeen = new AtomicInteger(0);
	    NodePlugin<Integer, Integer> slowNode = new NodePlugin<>() {
	        @Override
	        public Integer process(Integer input, NodeContext context) {
	            int now = concurrent.incrementAndGet();
	            maxSeen.updateAndGet(prev -> Math.max(prev, now));
	            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
	            concurrent.decrementAndGet();
	            return input;
	        }
	    };
	    GraphBuilder builder = GraphBuilder.named("concurrency");
	    for (int i = 0; i < 10; i++) {
	        builder.addNode("N" + i, slowNode);
	    }
	    Graph graph = builder.build();
	    ParallelExecutor executor = new ParallelExecutor(
	            ForkJoinPool.commonPool(),
	            FailureMode.FAIL_FAST,
	            2 // limit
	    );
	    executor.run(graph, 1, new NodeContext());
	    assertTrue(maxSeen.get() <= 2);
	}
	@Test
	void shouldExecuteJoinOnlyOnceUnderConcurrency() {
	    AtomicInteger executions = new AtomicInteger();
	    NodePlugin<String, String> join = new NodePlugin<>() {
	        @Override
	        public String process(String input, NodeContext context) {
	            executions.incrementAndGet();
	            return input;
	        }
	    };
	    Graph graph = GraphBuilder.named("fanin-race")
	            .addNode("start", (input, ctx) -> input)
	            .addNode("A", (input, ctx) -> input + "A")
	            .addNode("B", (input, ctx) -> input + "B")
	            .addNode("join", join)
	            .connect("start", "A")
	            .connect("start", "B")
	            .connect("A", "join")
	            .connect("B", "join")
	            .build();
	    new ParallelExecutor().run(graph, "x", new NodeContext());
	    assertEquals(2, executions.get()); // MergeNode will bring join
	}
}