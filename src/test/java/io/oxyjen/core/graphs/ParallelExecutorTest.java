package io.oxyjen.core.graphs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Graph;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;

class ParallelExecutorTest {
	@Test
    void runSingle_shouldReturnTerminalOutput() {
        NodePlugin<String, String> startNode = new NodePlugin<>() {
            @Override
            public String process(String input, NodeContext context) {
                return input + " world";
            }
            @Override
            public String getName() {
                return "start";
            }
        };
        NodePlugin<String, String> terminalNode = new NodePlugin<>() {
            @Override
            public String process(String input, NodeContext context) {
                return input.toUpperCase();
            }
            @Override
            public String getName() {
                return "terminal";
            }
        };
        Graph graph1 = Graph.builder("test-graph")
                .addNode("start", startNode)
                .addNode("terminal", terminalNode)
                .connect("start", "terminal")
                .build();
        Graph graph2 = Graph.builder("test-graph")
                .addNode("start", startNode)
                .addNode("terminal", terminalNode)
                .build();
        ParallelExecutor ex = new ParallelExecutor();
        NodeContext ctx = new NodeContext();
        String result = ex.runSingle(graph1, "hello", ctx);
        assertEquals("HELLO WORLD", result);
        IllegalStateException exp = assertThrows(
                IllegalStateException.class,
                () -> ex.runSingle(graph2, "input", ctx)
        );
        assertTrue(exp.getMessage().contains("requires exactly 1 terminal node"));
    }
}