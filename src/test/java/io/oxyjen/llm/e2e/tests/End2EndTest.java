package io.oxyjen.llm.e2e.tests;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMNode;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndLLMNodeTest {

    private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }

    @Test
    void graphExecutesAndStoresMemory() {
        log("End-to-End: Input → LLMNode → Output");

        // Arrange
        ChatModel model = new FakeChatModel();
        LLMNode node = LLMNode.builder()
            .model(model)
            .memory("chat")
            .build();

        NodeContext context = new NodeContext();

        // Act
        String output = node.process("hello", context);

        // Assert: output
        print("output", output);
        assertEquals("echo:hello", output);

        // Assert: memory
        Memory memory = context.memory("chat");
        print("memory entries", memory.entries());

        assertEquals(2, memory.entries().size());

        assertEquals("user", memory.entries().get(0).type());
        assertEquals("hello", memory.entries().get(0).value());

        assertEquals("assistant", memory.entries().get(1).type());
        assertEquals("echo:hello", memory.entries().get(1).value());
    }
}