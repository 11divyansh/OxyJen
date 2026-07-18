package io.oxyjen.llm.execution.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMResponse;
import io.oxyjen.llm.exceptions.NetworkException;

class FlakyModel implements ChatModel {
    int calls = 0;

    @Override
    public LLMResponse chat(String input) {
        calls++;
        if (calls == 1) {
            throw new NetworkException("temporary issue", null);
        }
        return LLMResponse.of("chain-ok");
    }
}
