package io.oxyjen.llm.schema.tests;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMResponse;

public class NewFakeModel implements ChatModel {
    private final String response;
    NewFakeModel(String response){ this.response=response; }
    public LLMResponse chat(String prompt){ return LLMResponse.of(response); }
}