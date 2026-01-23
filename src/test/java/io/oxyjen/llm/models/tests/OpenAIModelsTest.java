package io.oxyjen.llm.models.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.transport.openai.Models;

public class OpenAIModelsTest {
	
	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void supportedModelReturnsTrue() {
        log("Models.isSupported for known model");

        boolean supported = Models.isSupported("gpt-4o");

        print("isSupported(gpt-4o)", supported);

        assertTrue(supported);
    }

    @Test
    void unknownModelReturnsFalse() {
        log("Models.isSupported for unknown model");

        boolean supported = Models.isSupported("unknown-model");

        print("isSupported(unknown-model)", supported);

        assertFalse(supported);
    }

}
