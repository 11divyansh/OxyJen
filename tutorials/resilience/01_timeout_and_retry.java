package tutorials.resilience;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;

/**
 * Resilience tutorial 1:
 * Timeouts, retries, backoff, and jitter.
 */
final class TimeoutAndRetryTutorial {

    private TimeoutAndRetryTutorial() {}

    public static void main(String[] args) {
        ChatModel model = LLM.gemini("gemini/gemini-flash-latest");

        ChatModel resilient = LLMChain.builder()
            .primary(model)
            .retry(3)
            .timeout(Duration.ofSeconds(30))
            .exponentialBackoff()
            .maxBackoff(Duration.ofSeconds(60))
            .jitter(0.2)
            .build();

        System.out.println(resilient.getClass().getSimpleName());
    }
}
