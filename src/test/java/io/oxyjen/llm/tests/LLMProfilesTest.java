package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void unknownProfileThrowsException() {
        log("LLM.profile(unknown) throws exception");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> LLM.profile("does-not-exist")
        );

        print("exception message", ex.getMessage());

        assertTrue(ex.getMessage().contains("Unknown profile"));
    }

    @Test
    void registerProfileIsImmediatelyAvailable() {
        log("LLMProfiles.register creates retrievable profile");

        String profileName = "test-profile";
        String modelName = "gpt-4o-mini";

        //LLM.registerProfile(profileName, modelName); //v0.3+ feature

        ChatModel model = LLM.profile(profileName);

        print("registered profile", profileName);
        print("model class", model.getClass().getSimpleName());

        assertNotNull(model);
        assertTrue(model instanceof ChatModel);
    }


}