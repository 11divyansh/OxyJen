package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;

class ChatModelContractTest {

    private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void chatReturnsExpectedOutput() {
        log("ChatModel.chat returns expected output");

        ChatModel model = new FakeChatModel();

        String input = "hello";
        String output = model.chat(input);

        print("input", input);
        print("output", output);

        assertEquals("echo:hello", output);
    }

    /**
     * ChatModel does not retain hidden state
	 * consecutive calls are independent
	 * framework can safely reuse instances
     */
    @Test
    void chatHasNoStateLeakBetweenCalls() {
        log("ChatModel has no state leaks between calls");

        ChatModel model = new FakeChatModel();

        String out1 = model.chat("one");
        String out2 = model.chat("two");

        print("first call", out1);
        print("second call", out2);

        assertEquals("echo:one", out1);
        assertEquals("echo:two", out2);
    }

}