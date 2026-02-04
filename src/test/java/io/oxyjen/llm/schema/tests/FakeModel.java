package io.oxyjen.llm.schema.tests;

import io.oxyjen.llm.ChatModel;

public final class FakeModel implements ChatModel {

    private final String[] responses;
    private int index = 0;

    public FakeModel(String... responses) {
        this.responses = responses;
    }
    @Override
    public String chat(String prompt) {

        if (index < responses.length) {
            return responses[index++];
        }
        return responses[responses.length - 1];
    }
    public int callCount() {
        return index;
    }
}
