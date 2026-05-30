package io.oxyjen.core.graphs.branching.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.graph.ParallelExecutor;
import io.oxyjen.graph.branching.MergeNode;

class SuccessNode implements NodePlugin<Object, Object> {
    private final String name;
    private final Object output;

    SuccessNode(String name, Object output) {
        this.name = name;
        this.output = output;
    }

    @Override
    public Object process(Object input, NodeContext context) {
        return output;
    }

    @Override
    public String getName() {
        return name;
    }
}

class FailingNode implements NodePlugin<Object, Object> {
    private final String name;

    FailingNode(String name) {
        this.name = name;
    }

    @Override
    public Object process(Object input, NodeContext context) {
        throw new RuntimeException("Failure in " + name);
    }

    @Override
    public String getName() {
        return name;
    }
}

class MergeNodeWithExecutor {
	@Test
	void shouldMergeAllSuccessfulNodes() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .build("merge");
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "a"))
	            .addNode("B", new SuccessNode("B", "b"))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    Map<String, Object> result = executor.run(graph, null, context);
	    MergeNode.MergeResult mergeResult =
	            (MergeNode.MergeResult) result.get("merge");
	    assertEquals(2, mergeResult.getSuccess().size());
	    assertTrue(mergeResult.getErrors().isEmpty());
	}
	@Test
	void shouldCollectErrorsFromFailingNodes() {
	    NodeContext context = new NodeContext();

	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .build("merge");

	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "ok"))
	            .addNode("B", new FailingNode("B"))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connectOnFailure("B", "merge")
	            .build();

	    ParallelExecutor executor = new ParallelExecutor(
	            ExecutionRuntime.builder()
	                    .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	                    .build()
	    );
	    Map<String, Object> result = executor.run(graph, null, context);
	    MergeNode.MergeResult mergeResult =
	            (MergeNode.MergeResult) result.get("merge");
	    assertEquals(1, mergeResult.getSuccess().size());
	    assertEquals(1, mergeResult.getErrors().size());
	}
	@Test
	void shouldNotReachMergeWithoutFailureEdge() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .timeout(500, TimeUnit.MILLISECONDS)
	            .build("merge");
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "ok"))
	            .addNode("B", new FailingNode("B"))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connect("B", "merge") // failure won't propagate
	            .build();
	    ParallelExecutor executor = new ParallelExecutor(
	            ExecutionRuntime.builder()
	                    .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	                    .build()
	    );
	    assertThrows(
	            MergeNode.MergeTimeoutException.class,
	            () -> executor.run(graph, null, context)
	    );
	}
	@Test
	void shouldRouteFailureToMergeNode() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .build("merge");
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "ok"))
	            .addNode("B", new FailingNode("B"))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connectOnFailure("B", "merge") 
	            .build();
	    ParallelExecutor executor = new ParallelExecutor(
	            ExecutionRuntime.builder()
	                    .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	                    .build()
	    );
	    Map<String, Object> result = executor.run(graph, null, context);
	    MergeNode.MergeResult mergeResult =
	            (MergeNode.MergeResult) result.get("merge");
	    assertEquals(1, mergeResult.getSuccess().size());
	    assertEquals(1, mergeResult.getErrors().size());
	}
	@Test
	void shouldRouteAnyNodeFailureToMergeNode() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .build("merge");
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "ok"))
	            .addNode("B", new FailingNode("B"))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .connectAnyFailureTo("merge")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor(
	            ExecutionRuntime.builder()
	                    .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	                    .build()
	    );
	    Map<String, Object> result = executor.run(graph, null, context);
	    MergeNode.MergeResult mergeResult =
	            (MergeNode.MergeResult) result.get("merge");
	    assertEquals(1, mergeResult.getSuccess().size());
	    assertEquals(1, mergeResult.getErrors().size());
	    assertTrue(mergeResult.getErrors().containsKey("B"));
	}
	@Test
	void anyFailureRouteShouldNotHideSuccessfulTerminalOutput() {
	    NodeContext context = new NodeContext();
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "ok"))
	            .addNode("failureSink", new SuccessNode("failureSink", "handled"))
	            .connectAnyFailureTo("failureSink")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor(
	            ExecutionRuntime.builder()
	                    .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
	                    .build()
	    );
	    Map<String, Object> result = executor.run(graph, null, context);
	    assertEquals("ok", result.get("A"));
	}
	@Test
	void shouldHandleMultipleParallelBranches() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B", "C")
	            .build("merge");
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", 1))
	            .addNode("B", new SuccessNode("B", 2))
	            .addNode("C", new SuccessNode("C", 3))
	            .addNode("merge", merge)
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .connect("C", "merge")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    Map<String, Object> result = executor.run(graph, null, context);
	    MergeNode.MergeResult mergeResult =
	            (MergeNode.MergeResult) result.get("merge");
	    assertEquals(3, mergeResult.getSuccess().size());
	}
	@Test
	void downstreamNodeShouldConsumeMergeResult() {
	    NodeContext context = new NodeContext();
	    MergeNode merge = new MergeNode.Builder()
	            .expect("A", "B")
	            .build("merge");
	    NodePlugin<Object, Object> consumer = new NodePlugin<>() {
	        @Override
	        public Object process(Object input, NodeContext ctx) {
	            MergeNode.MergeResult result = (MergeNode.MergeResult) input;
	            return result.getSuccess().size();
	        }
	        @Override
	        public String getName() {
	            return "consumer";
	        }
	    };
	    Graph graph = GraphBuilder.named("test")
	            .addNode("A", new SuccessNode("A", "x"))
	            .addNode("B", new SuccessNode("B", "y"))
	            .addNode("merge", merge)
	            .addNode("consumer", consumer)
	            .connect("A", "merge")
	            .connect("B", "merge")
	            .connect("merge", "consumer")
	            .build();
	    ParallelExecutor executor = new ParallelExecutor();
	    Map<String, Object> result = executor.run(graph, null, context);
	    assertEquals(2, result.get("consumer"));
	}
}
