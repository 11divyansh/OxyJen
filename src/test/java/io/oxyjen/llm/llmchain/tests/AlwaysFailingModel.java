package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.RateLimitException;

class AlwaysFailingModel implements ChatModel {

    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        throw new RateLimitException("rate limited");
    }
}
