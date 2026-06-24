package tutorials.resilience;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.llm.LLMChain;
import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;
import io.oxyjen.semantics.retry.RetryPolicy;

/**
 * Resilience tutorial 3:
 * Use RetryPolicy with LLMChain and keep the rate limiter as the real guardrail.
 *
 * Key rule:
 * - RateLimiter prevents parallel oversubmission.
 * - RetryPolicy handles transient failures and Retry-After hints.
 * - Do not rely on retry backoff as the main quota control.
 */
final class LLMChainWithRetryPolicyTutorial {

    private LLMChainWithRetryPolicyTutorial() {}

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");

        RateLimiter limiter = RateLimiters.geminiFreeTier();

        ChatModel primary = LLM.withRateLimit(
                LLM.gemini("gemini/gemini-flash-latest", apiKey),
                limiter
        );

        ChatModel fallback = LLM.withRateLimit(
                LLM.gemini("gemini/gemini-2.0-flash-lite", apiKey),
                limiter
        );

        RetryPolicy policy = RetryPolicy.builder()
                .maxAttempts(3)
                .baseBackoff(Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(10))
                .retryOn(
                        io.oxyjen.llm.exceptions.RateLimitException.class,
                        io.oxyjen.llm.exceptions.TimeoutException.class,
                        io.oxyjen.llm.exceptions.NetworkException.class
                )
                .failOn(io.oxyjen.llm.exceptions.InvalidAPIKeyException.class)
                .jitter(0.10)
                .build();

        LLMChain chain = LLMChain.builder()
                .primary(primary)
                .fallback(fallback)
                .retryPolicy(policy)
                .timeout(Duration.ofSeconds(60))
                .build();

        System.out.println(chain.chat("Summarize the retry policy example in one sentence."));
    }
}