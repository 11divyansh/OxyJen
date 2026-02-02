package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;

class NoopModel implements ChatModel {
    @Override
    public String chat(String input) {
        return "ok";
    }
}
