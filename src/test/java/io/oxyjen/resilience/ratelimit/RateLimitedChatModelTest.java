package io.oxyjen.resilience.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.exceptions.RateLimitException;

class RateLimitedChatModelTest {

    @Test
    void updatesAdaptiveLimiterEvenWhenRetryAfterIsMissing() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter(1_000);
        ChatModel model = new RateLimitedChatModel(input -> {
            throw new RateLimitException("rate limited");
        }, limiter);

        long before = System.currentTimeMillis();
        assertThrows(RateLimitException.class, () -> model.chat("hello"));

        assertTrue(
            limiter.getBlockedUntil() - before >= 25_000L,
            "adaptive limiter should enter a cooldown after 429 without Retry-After"
        );
    }

    @Test
    void passesThroughSuccessfulResponsesWithoutBlocking() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter(1_000);
        ChatModel model = new RateLimitedChatModel(input -> "ok", limiter);

        assertDoesNotThrow(() -> model.chat("hello"));
    }
}
