package io.oxyjen.llm.llmchain.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.TokenLimitExceededException;

class TokenLimitFailingModel implements ChatModel {

    int calls = 0;

    @Override
    public String chat(String input) {
        calls++;
        throw new TokenLimitExceededException(
            "Token limit exceeded",
            8192
        );
    }
}
