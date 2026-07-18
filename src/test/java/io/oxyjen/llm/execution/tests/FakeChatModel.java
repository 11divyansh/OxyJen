package io.oxyjen.llm.execution.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMResponse;

class FakeChatModel implements ChatModel {
    @Override
    public LLMResponse chat(String input) {
        return LLMResponse.of("echo:" + input);
    }
}
