package io.oxyjen.llm.llmchain.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.llm.exceptions.TokenLimitExceededException;

public class LLMChainRetryTest {

	private void log(String title) {
        System.out.println("\n==============================");
        System.out.println(title);
        System.out.println("==============================");
    }

    private void print(String label, Object value) {
        System.out.println(label + " => " + value);
    }
    @Test
    void retriesOnTransientErrorAndSucceeds() {
        log("LLMChain retries on RateLimitException");

        FlakyChatModel model = new FlakyChatModel();

        ChatModel chain = LLMChain.builder()
            .primary(model)
            .retry(3)
            .build();

        String result = chain.chat("hello");

        print("result", result);
        print("calls", model.calls);

        assertEquals("success", result);
        assertEquals(2, model.calls);
    }
    @Test
    void doesNotRetryOnNonTransientError() {
        log("LLMChain does not retry on TokenLimitExceededException");

        TokenLimitFailingModel model = new TokenLimitFailingModel();

        ChatModel chain = LLMChain.builder()
            .primary(model)
            .retry(3)
            .build();

        TokenLimitExceededException ex = assertThrows(
            TokenLimitExceededException.class,
            () -> chain.chat("hello")
        );

        print("exception", ex.getMessage());
        print("calls", model.calls);

        assertEquals(1, model.calls);
    }

}
