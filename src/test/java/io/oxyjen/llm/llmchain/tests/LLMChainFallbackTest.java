package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMChain;

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

}
