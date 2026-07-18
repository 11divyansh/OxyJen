package io.oxyjen.llm.schema.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMResponse;

public final class FakeModel implements ChatModel {

    private final String[] responses;
    private int index = 0;

    public FakeModel(String... responses) {
        this.responses = responses;
    }
    @Override
    public LLMResponse chat(String prompt) {

        if (index < responses.length) {
            return LLMResponse.of(responses[index++]);
        }
        return LLMResponse.of(responses[responses.length - 1]);
    }
    public int callCount() {
        return index;
    }
}
