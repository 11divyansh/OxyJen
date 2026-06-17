package tutorials.llm;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;

/**
 * LLM tutorial 1:
 * Retry, fallback, backoff, and jitter.
 */
final class ChainAndFallbackTutorial {

    private ChainAndFallbackTutorial() {}

    public static void main(String[] args) {
        ChatModel primary = LLM.gemini("gemini/gemini-flash-latest");
        ChatModel fallback = LLM.gemini("gemini/gemini-2.0-flash-lite");

        ChatModel chain = LLMChain.builder()
            .primary(primary)
            .fallback(fallback)
            .retry(3)
            .timeout(Duration.ofSeconds(45))
            .exponentialBackoff()
            .maxBackoff(Duration.ofSeconds(60))
            .jitter(0.15)
            .build();

        System.out.println(chain.chat("Explain Oxyjen in one sentence."));
    }
}
