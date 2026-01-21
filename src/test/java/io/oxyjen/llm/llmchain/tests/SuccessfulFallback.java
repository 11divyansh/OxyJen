package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;

public class SuccessfulFallback implements ChatModel {

    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        return "fallback-success";
    }
}
