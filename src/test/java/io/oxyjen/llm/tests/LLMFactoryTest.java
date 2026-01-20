package io.oxyjen.llm.tests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;

public class LLMFactoryTest {

	//setx OPENAI_API_KEY "sk-test"
	//setting key because it is checked for presence
	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void ofGpt4oReturnsChatModel() {
        log("LLM.of(\"gpt-4o\") returns ChatModel");

        ChatModel model = LLM.openai("gpt-4o","sk-test");

        print("model class", model.getClass().getSimpleName());

        assertNotNull(model);
        assertTrue(model instanceof ChatModel);
    }
    @Test
    void ofGpt4oMiniReturnsChatModel() {
        log("LLM.of(\"gpt-4o-mini\") returns ChatModel");

        ChatModel model = LLM.of("gpt-4o-mini");

        print("model class", model.getClass().getSimpleName());

        assertNotNull(model);
        assertTrue(model instanceof ChatModel);
    }

    @Test
    void invalidModelThrowsException() {
        log("LLM.of(invalid-model) throws IllegalArgumentException");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> LLM.of("invalid-model")
        );

        print("exception message", ex.getMessage());

        assertTrue(ex.getMessage().contains("Unknown model"));
    }

    @Test
    void nullModelThrowsException() {
        log("LLM.of(null) throws IllegalArgumentException");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> LLM.of(null)
        );

        print("exception message", ex.getMessage());

        assertTrue(ex.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void emptyModelThrowsException() {
        log("LLM.of(\"\") throws IllegalArgumentException");

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> LLM.of("   ")
        );

        print("exception message", ex.getMessage());

        assertTrue(ex.getMessage().contains("cannot be null or empty"));
    }


}
