package io.oxyjen.llm.schema.tests;

import io.oxyjen.llm.ChatModel;

public class NewFakeModel implements ChatModel {
    private final String response;
    NewFakeModel(String response){ this.response=response; }
    public String chat(String prompt){ return response; }
}