package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.exceptions.LLMException;

public class LLMChainFallbackTest {

	 private void log(String title) {
	        System.out.println("\n==============================");
	        System.out.println(title);
	        System.out.println("==============================");
	    }

	    private void print(String label, Object value) {
	        System.out.println(label + " => " + value);
	    }
	    @Test
	    void fallbackSucceedsWhenPrimaryFails() {
	        log("LLMChain fallback succeeds when primary fails");

	        AlwaysFailingPrimary primary = new AlwaysFailingPrimary();
	        SuccessfulFallback fallback = new SuccessfulFallback();

	        ChatModel chain = LLMChain.builder()
	            .primary(primary)
	            .fallback(fallback)
	            .retry(2)   // retries happen per model
	            .build();

	        String result = chain.chat("hello");

	        print("result", result);
	        print("primary calls", primary.calls);
	        print("fallback calls", fallback.calls);

	        assertEquals("fallback-success", result);
	        assertEquals(2, primary.calls);   // retried then moved on
	        assertEquals(1, fallback.calls);  // succeeds immediately
	    }

	    @Test
	    void allModelsFailThrowsLLMException() {
	        log("LLMChain throws LLMException when all models fail");

	        AlwaysFailingModel primary = new AlwaysFailingModel();
	        AlwaysFailingModel fallback = new AlwaysFailingModel();

	        ChatModel chain = LLMChain.builder()
	            .primary(primary)
	            .fallback(fallback)
	            .retry(1)
	            .build();

	        LLMException ex = assertThrows(
	            LLMException.class,
	            () -> chain.chat("hello")
	        );

	        print("exception message", ex.getMessage());
	        print("primary calls", primary.calls);
	        print("fallback calls", fallback.calls);

	        assertTrue(ex.getMessage().contains("still failing"));
	        assertEquals(1, primary.calls);
	        assertEquals(1, fallback.calls);
	    }

}
