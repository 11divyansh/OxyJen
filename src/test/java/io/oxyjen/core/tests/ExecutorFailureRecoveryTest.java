package io.oxyjen.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Executor;
import io.oxyjen.core.Graph;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

class ExecutorFailureRecoveryTest {

    @Test
    void testFailureRecoveryStopsPipeline() {
        Graph graph = new Graph("failure-graph");
        NodeContext context = new NodeContext();
        Executor executor = new Executor();

        graph.addNode(new NodeA());
        graph.addNode(new ErrorNode());
        graph.addNode(new NodeC()); // MUST NOT RUN

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                executor.run(graph, "input", context)
        );

        // Validate meaningful error
        assertTrue(ex.getMessage().contains("ErrorNode"));
    }

    // -------------------------
    // Test Nodes
    // -------------------------

    static class NodeA implements NodePlugin<String, String> {
        @Override
        public String getName() {
            return "NodeA";
        }

        @Override
        public String process(String input, NodeContext ctx) {
            return input + "A";
        }
    }

    static class ErrorNode implements NodePlugin<String, String> {
        @Override
        public String getName() {
            return "ErrorNode";
        }

        @Override
        public String process(String input, NodeContext ctx) {
            throw new RuntimeException("Intentional test exception from ErrorNode");
        }
    }

    static class NodeC implements NodePlugin<String, String> {
        @Override
        public String getName() {
            return "NodeC";
        }

        @Override
        public String process(String input, NodeContext ctx) {
            fail("NodeC should NOT be executed after failure");
            return null;
        }
    }
    
    @Test
    void testOnErrorCalledAndOnFinishNotCalledOnFailure() {
        // Arrange
        Graph graph = new Graph("error-graph");
        NodeContext context = new NodeContext();
        Executor executor = new Executor();

        AtomicBoolean onErrorCalled = new AtomicBoolean(false);
        AtomicBoolean onFinishCalled = new AtomicBoolean(false);

        NodePlugin<String, String> failingNode = new NodePlugin<String,String>() {

            @Override
            public String getName() {
                return "FailingNode";
            }

            @Override
            public void onStart(NodeContext context) {
                // no-op
            }

            @Override
            public String process(String input, NodeContext context) {
                throw new RuntimeException("Boom!");
            }

            @Override
            public void onFinish(NodeContext context) {
                onFinishCalled.set(true);
            }

            @Override
            public void onError(Exception e, NodeContext context) {
                onErrorCalled.set(true);
            }
        };

        graph.addNode(failingNode);

        // Act
        RuntimeException ex = assertThrows(
            RuntimeException.class,
            () -> executor.run(graph, "input", context)
        );

        // Assert
        assertEquals("Node failed: FailingNode", ex.getMessage());
        assertTrue(onErrorCalled.get(), "onError should be called on failure");
        assertFalse(onFinishCalled.get(), "onFinish should NOT be called on failure");
    }

}
