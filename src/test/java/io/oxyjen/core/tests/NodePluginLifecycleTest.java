package io.oxyjen.core.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.oxyjen.core.Executor;
import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.core.exceptions.ExceptionHandler;

class NodePluginLifecycleTest {

    private Executor executor;

    @BeforeEach
    void setup() {
        // create your Executor similarly to how it's constructed in your main code
        // If Executor requires configuration, pass a default config or use a test ctor
        executor = new Executor();
    }

    @Test
    void testLifecycle_basicSync() {
        DummyLifecycleNode node = new DummyLifecycleNode();
        NodeContext ctx = new NodeContext();
        
        Graph graph = new GraphBuilder()
                .addNode(node)
                .build();

        // If your Executor.run is synchronous: run(node, input, ctx)
        String out = executor.run(graph, "hello", ctx);

        assertEquals("HELLO", out, "process() should transform to upper-case");
        assertTrue(node.started, "onStart should have been called");
        assertTrue(node.finished, "onFinish should have been called");
    }

    @Test
    void testExceptionPropagation() {
        ErrorNode node = new ErrorNode();
        NodeContext ctx = new NodeContext();

        Graph graph = new GraphBuilder()
                .addNode(node)
                .build();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            executor.run(graph, "hello", ctx);
        });

        assertTrue(ex.getCause().getMessage().contains("Intentional test exception"));
    }
    
    @Test
    void testNodeThrowsException_withNodeName() {
        ErrorNode node = new ErrorNode();      // Node that intentionally throws
        NodeContext ctx = new NodeContext();   // Context

        Graph graph = new GraphBuilder()
                .addNode(node)
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            executor.run(graph, "input", ctx);
        });

        // Assert exception message contains the node name
        assertTrue(ex.getMessage().contains("ErrorNode"),
            "Exception message should include the node name: " + ex.getMessage());
    }


    @Test
    void testCustomExceptionHandlerIsInvoked() {
        // Custom capturing handler based on your API
        class CapturingHandler implements ExceptionHandler {
            NodePlugin<?, ?> nodeRef;
            Exception exceptionRef;
            NodeContext ctxRef;

            @Override
            public void handleException(NodePlugin<?, ?> node, Exception e, NodeContext context) {
                this.nodeRef = node;
                this.exceptionRef = e;
                this.ctxRef = context;
                // do NOT throw — swallow it intentionally for testing
            }
        }

        CapturingHandler handler = new CapturingHandler();
        NodeContext ctx = new NodeContext();
        ctx.setExceptionHandler(handler);

        ErrorNode node = new ErrorNode();

        Graph graph = new GraphBuilder()
                .addNode(node)
                .build();

        try {
            executor.run(graph, "hello", ctx);
        } catch (RuntimeException ignored) {
            // default handler throws
            // but our custom one should not throw
        }

        // Validate handler was invoked
        assertNotNull(handler.nodeRef, "ExceptionHandler should receive the node");
        assertNotNull(handler.exceptionRef, "ExceptionHandler should receive the exception");
        assertEquals("ErrorNode", handler.nodeRef.getName());
        assertTrue(handler.exceptionRef.getMessage().contains("Intentional test exception"));
    }
    
    @Test
    void testNodeContext_sharesStateBetweenNodes() {
        NodeContext ctx = new NodeContext();

        // Node 1: writes to context
        NodePlugin<String, String> writerNode = new NodePlugin<String,String>() {
            @Override
            public String process(String input, NodeContext context) {
                context.set("sharedKey", input.toUpperCase());
                return input;
            }

            @Override
            public String getName() {
                return "WriterNode";
            }
        };

        // Node 2: reads from context
        NodePlugin<String, String> readerNode = new NodePlugin<String,String>() {
            @Override
            public String process(String input, NodeContext context) {
            	 String sharedValue = (String) context.get("sharedKey");
                 System.out.println("ReaderNode received: " + sharedValue);
                 return sharedValue;
            }

            @Override
            public String getName() {
                return "ReaderNode";
            }
        };

        // Build graph: writer -> reader
        Graph graph = new GraphBuilder()
                .addNode(writerNode)
                .addNode(readerNode)
                .build();

        // Execute
        executor.run(graph, "hello", ctx);

        // Verify that readerNode read the value set by writerNode
        String valueFromContext = (String) ctx.get("sharedKey");
        assertEquals("HELLO", valueFromContext, "NodeContext should share state between nodes");
    }
    @Test
    void testNodeContext_metadataTracking() {
        NodeContext ctx = new NodeContext();

        // Node 1: record start time
        NodePlugin<String, String> startTimeNode = new NodePlugin<String, String>() {
            @Override
            public String process(String input, NodeContext context) {
                context.set("startTime", System.currentTimeMillis());
                context.set("nodeName", getName());
                return input;
            }

            @Override
            public String getName() {
                return "StartTimeNode";
            }
        };

        // Node 2: record end time
        NodePlugin<String, String> endTimeNode = new NodePlugin<String, String>() {
            @Override
            public String process(String input, NodeContext context) {
                context.set("endTime", System.currentTimeMillis());
                context.set("nodeName2", getName());
                return input;
            }

            @Override
            public String getName() {
                return "EndTimeNode";
            }
        };

        // Build the graph
        Graph graph = new GraphBuilder()
                .addNode(startTimeNode)
                .addNode(endTimeNode)
                .build();

        // Execute
        executor.run(graph, "hello", ctx);

        // Retrieve metadata
        long start = (Long) ctx.get("startTime");
        long end = (Long) ctx.get("endTime");
        String node1 = (String) ctx.get("nodeName");
        String node2 = (String) ctx.get("nodeName2");

        // Assertions
        assertNotNull(start, "Start time should be recorded");
        assertNotNull(end, "End time should be recorded");
        assertTrue(end >= start, "End time should be after start time");
        assertEquals("StartTimeNode", node1);
        assertEquals("EndTimeNode", node2);

        // Optional: display metadata
        System.out.println("Metadata tracking:");
        System.out.println("Node 1: " + node1 + ", startTime=" + start);
        System.out.println("Node 2: " + node2 + ", endTime=" + end);
    }
    
    @Test
    void testEmptyGraph_throwsIllegalStateException() {
        // Arrange
        Graph graph = new Graph(); // unnamed-graph, no nodes
        Executor executor = new Executor();
        NodeContext context = new NodeContext();

        // Act + Assert
        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> executor.run(graph, "input", context)
        );

        // Verify message
        assertEquals(
            "Graph [unnamed-graph] contains no nodes",
            ex.getMessage()
        );
    }

    @Test
    void testSequentialExecutionOrder() {
        Executor executor = new Executor();
        NodeContext context = new NodeContext();

        // Shared execution order list
        List<String> executionOrder = new ArrayList<>();
        context.set("executionOrder", executionOrder);

        NodePlugin<String, String> nodeA = new NodePlugin<String, String>() {
            @Override
            public String process(String input, NodeContext ctx) {
                ((List<String>) ctx.get("executionOrder")).add("NodeA");
                return input + "A";
            }

            @Override
            public String getName() {
                return "NodeA";
            }
        };

        NodePlugin<String, String> nodeB = new NodePlugin<String, String>() {
            @Override
            public String process(String input, NodeContext ctx) {
                ((List<String>) ctx.get("executionOrder")).add("NodeB");
                return input + "B";
            }

            @Override
            public String getName() {
                return "NodeB";
            }
        };

        NodePlugin<String, String> nodeC = new NodePlugin<String, String>() {
            @Override
            public String process(String input, NodeContext ctx) {
                ((List<String>) ctx.get("executionOrder")).add("NodeC");
                return input + "C";
            }

            @Override
            public String getName() {
                return "NodeC";
            }
        };

        Graph graph = new Graph("order-test-graph")
                .addNode(nodeA)
                .addNode(nodeB)
                .addNode(nodeC);

        // Act
        String result = executor.run(graph, "", context);

        // Assert output chaining
        System.out.println("Result: "+result);
        System.out.println("List:"+executionOrder);
        assertEquals("ABC", result);

        // Assert execution order
        assertEquals(
                List.of("NodeA", "NodeB", "NodeC"),
                executionOrder
        );
    }

    @Test
    void testLifecycleLoggingOrder() {
        Executor executor = new Executor();
        NodeContext context = new NodeContext();

        List<String> lifecycleEvents = new ArrayList<>();
        context.set("events", lifecycleEvents);

        NodePlugin<String, String> lifecycleNode = new NodePlugin<String, String>() {

            @Override
            public void onStart(NodeContext ctx) {
                ctx.getLogger().info("onStart called");
                ((List<String>) ctx.get("events")).add("onStart");
            }

            @Override
            public String process(String input, NodeContext ctx) {
                ctx.getLogger().info("process called");
                ((List<String>) ctx.get("events")).add("process");
                return input + "X";
            }

            @Override
            public void onFinish(NodeContext ctx) {
                ctx.getLogger().info("onFinish called");
                ((List<String>) ctx.get("events")).add("onFinish");
            }

            @Override
            public String getName() {
                return "LifecycleNode";
            }
        };

        Graph graph = new Graph("lifecycle-test-graph")
                .addNode(lifecycleNode);

        // Act
        String result = executor.run(graph, "", context);
        System.out.println("Result:"+result);

        // Assert output propagation
        assertEquals("X", result);

        // Assert lifecycle order
        assertEquals(
                List.of("onStart", "process", "onFinish"),
                lifecycleEvents
        );
    }

    @Test
    void testTypePropagationAcrossNodes() {
        Executor executor = new Executor();
        NodeContext context = new NodeContext();

        // Node 1: String -> Integer
        NodePlugin<String, Integer> stringToIntNode =
                new NodePlugin<String, Integer>() {

            @Override
            public Integer process(String input, NodeContext ctx) {
                return input.length();
            }

            @Override
            public String getName() {
                return "StringToIntNode";
            }
        };

        // Node 2: Integer -> String
        NodePlugin<Integer, String> intToStringNode =
                new NodePlugin<Integer, String>() {

            @Override
            public String process(Integer input, NodeContext ctx) {
                return "Length is: " + input;
            }

            @Override
            public String getName() {
                return "IntToStringNode";
            }
        };

        Graph graph = new Graph("type-propagation-graph")
                .addNode(stringToIntNode)
                .addNode(intToStringNode);

        // Act
        String result = executor.run(graph, "HELLO", context);

        // Assert
        assertEquals("Length is: 5", result);
    }


}
