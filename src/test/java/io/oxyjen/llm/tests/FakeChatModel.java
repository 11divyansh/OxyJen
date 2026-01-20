package io.oxyjen.llm.tests;

import io.oxyjen.llm.ChatModel;

public class FakeChatModel implements ChatModel {
    @Override
    public String chat(String input) {
        return "echo:" + input;
    }
}
