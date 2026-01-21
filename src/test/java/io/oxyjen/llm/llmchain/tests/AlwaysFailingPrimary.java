package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.NetworkException;

class AlwaysFailingPrimary implements ChatModel {

    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        throw new NetworkException("network down", null);
    }
}
