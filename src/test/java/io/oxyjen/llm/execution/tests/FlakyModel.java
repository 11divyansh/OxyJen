package io.oxyjen.llm.execution.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.NetworkException;

class FlakyModel implements ChatModel {
    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        if (calls == 1) {
            throw new NetworkException("temporary issue", null);
        }
        return "chain-ok";
    }
}
