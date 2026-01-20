package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.exceptions.LLMException;

public class LLMChainBackoffTest {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

	@Test
	void fixedBackoffUsesConstantDelay() {
	    log("LLMChain fixed backoff uses constant delay");

	    AlwaysFailingModel model = new AlwaysFailingModel();

	    ChatModel chain = LLMChain.builder()
	        .primary(model)
	        .retry(3)
	        .fixedBackoff()
	        .build();

	    assertThrows(
	        LLMException.class,
	        () -> chain.chat("hello")
	    );

	    System.out.println("calls => " + model.calls);

	    assertEquals(3, model.calls);
	}

	@Test
	void exponentialBackoffUsesIncreasingDelay() {
	    log("LLMChain exponential backoff uses increasing delay");

	    AlwaysFailingModel model = new AlwaysFailingModel();

	    ChatModel chain = LLMChain.builder()
	        .primary(model)
	        .retry(3)
	        .exponentialBackoff()
	        .build();

	    assertThrows(
	        LLMException.class,
	        () -> chain.chat("hello")
	    );

	    System.out.println("calls => " + model.calls);

	    assertEquals(3, model.calls);
	}

}
