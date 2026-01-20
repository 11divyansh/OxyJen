package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.transport.openai.OpenAIChatModel;

class LLMProfilesTest {

    private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void defaultProfilesExist() {
        log("LLMProfiles default profiles exist");

        ChatModel fast = LLM.profile("fast");
        ChatModel cheap = LLM.profile("cheap");
        ChatModel smart = LLM.profile("smart");

        print("fast", fast.getClass().getSimpleName());
        if(fast instanceof OpenAIChatModel m) {
        	print("Model used",m.getModelName());
        }
        print("cheap", cheap.getClass().getSimpleName());
        if(cheap instanceof OpenAIChatModel m) {
        	print("Model used",m.getModelName());
        }
        print("smart", smart.getClass().getSimpleName());

        assertNotNull(fast);
        assertNotNull(cheap);
        assertNotNull(smart);
    }
    @Test
    void fastProfileReturnsChatModel() {
        log("LLM.profile(\"fast\") returns ChatModel");

        ChatModel model = LLM.profile("fast");

        print("model class", model.getClass().getSimpleName());

        assertNotNull(model);
        assertTrue(model instanceof ChatModel);
    }


}