package tutorials.resilience;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;

/**
 * Resilience tutorial 2:
 * Combine LLMChain with a shared rate limiter.
 */
final class LLMChainWithLimiterTutorial {

    private LLMChainWithLimiterTutorial() {}

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        ChatModel base = LLM.gemini("gemini/gemini-flash-latest", apiKey);

        RateLimiter limiter = RateLimiters.geminiFreeTier();
        ChatModel limited = LLM.withRateLimit(base, limiter);

        ChatModel resilient = LLMChain.builder()
            .primary(limited)
            .fallback(LLM.withRateLimit(LLM.gemini("gemini/gemini-2.0-flash-lite", apiKey), limiter))
            .retry(3)
            .timeout(Duration.ofSeconds(60))
            .exponentialBackoff()
            .maxBackoff(Duration.ofSeconds(60))
            .jitter(0.15)
            .build();

        System.out.println(resilient.getClass().getSimpleName());
    }
}

