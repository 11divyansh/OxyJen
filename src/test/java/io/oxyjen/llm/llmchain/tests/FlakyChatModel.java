package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.RateLimitException;

class FlakyChatModel implements ChatModel {

    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        if (calls == 1) {
            throw new RateLimitException("Rate limit");
        }
        return "success";
    }
}
