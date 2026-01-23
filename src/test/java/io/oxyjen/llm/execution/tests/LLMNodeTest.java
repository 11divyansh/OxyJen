package io.oxyjen.llm.execution.tests;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.LLMNode;

public class LLMNodeTest {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void inputFlowsThroughNodeAndReturnsOutput() {
        log("LLMNode passes input to model and returns output");

        ChatModel model = new FakeChatModel();
        LLMNode node = LLMNode.builder()
            .model(model)
            .build();

        NodeContext context = new NodeContext();

        String input = "hello";
        String output = node.process(input, context);

        print("input", input);
        print("output", output);

        assertEquals("echo:hello", output);
    }
    @Test
    void nodeWritesUserAndAssistantMessagesToMemory() {
        log("LLMNode writes input and output to memory");

        ChatModel model = new FakeChatModel();
        LLMNode node = LLMNode.builder()
            .model(model)
            .memory("chat")
            .build();

        NodeContext context = new NodeContext();

        node.process("hi", context);

        Memory memory = context.memory("chat");

        print("memory entries", memory.entries());

        assertEquals(2, memory.entries().size());

        assertEquals("user", memory.entries().get(0).type());
        assertEquals("hi", memory.entries().get(0).value());

        assertEquals("assistant", memory.entries().get(1).type());
        assertEquals("echo:hi", memory.entries().get(1).value());
    }

    @Test
    void nodeNameIsStable() {
        log("LLMNode name is stable");

        ChatModel model = new FakeChatModel();
        LLMNode node = LLMNode.builder()
            .model(model)
            .build();

        String name1 = node.getName();
        String name2 = node.getName();

        print("name1", name1);
        print("name2", name2);

        assertEquals(name1, name2);
        assertTrue(name1.startsWith("LLM["));
    }

    @Test
    void nodeAccumulatesMemoryAcrossMultipleRuns() {
        log("LLMNode accumulates memory across multiple executions");

        ChatModel model = new FakeChatModel();
        LLMNode node = LLMNode.builder()
            .model(model)
            .memory("chat")
            .build();

        NodeContext context = new NodeContext();

        // First run
        String out1 = node.process("hello", context);

        // Second run(same context)
        String out2 = node.process("world", context);

        Memory memory = context.memory("chat");

        print("output1", out1);
        print("output2", out2);
        print("memory entries", memory.entries());

        assertEquals(4, memory.entries().size());

        assertEquals("user", memory.entries().get(0).type());
        assertEquals("hello", memory.entries().get(0).value());

        assertEquals("assistant", memory.entries().get(1).type());
        assertEquals("echo:hello", memory.entries().get(1).value());

        assertEquals("user", memory.entries().get(2).type());
        assertEquals("world", memory.entries().get(2).value());

        assertEquals("assistant", memory.entries().get(3).type());
        assertEquals("echo:world", memory.entries().get(3).value());
    }

//    @Test
//    void nodeExecutesWithProfileBasedModel() {
//        log("LLMNode executes with profile-based model");
//
//        // Profile resolves to ChatModel
//        ChatModel model = LLM.profile("fast");
//
//        LLMNode node = LLMNode.builder()
//            .model(model)
//            .build();
//
//        NodeContext context = new NodeContext();
//
//        // We don't assert output content (OpenAI-backed)
//        // We assert NO type / cast errors
//        assertDoesNotThrow(() -> {
//            node.process("hello", context);
//        });
//
//        print("node name", node.getName());
//    }

    @Test
    void nodeExecutesWithChainBasedModel() {
        log("LLMNode executes with LLMChain");

        FlakyModel primary = new FlakyModel();

        ChatModel chain = LLMChain.builder()
            .primary(primary)
            .retry(2)
            .build();

        LLMNode node = LLMNode.builder()
            .model(chain)
            .build();

        NodeContext context = new NodeContext();

        String result = assertDoesNotThrow(() ->
            node.process("hello", context)
        );

        print("result", result);
        print("primary calls", primary.calls);

        assertEquals("chain-ok", result);
    }


}
