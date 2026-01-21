package io.oxyjen.llm.execution.tests;

import io.oxyjen.llm.ChatModel;

class FakeChatModel implements ChatModel {
    @Override
    public String chat(String input) {
        return "echo:" + input;
    }
}
