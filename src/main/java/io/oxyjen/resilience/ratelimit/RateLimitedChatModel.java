package io.oxyjen.resilience.ratelimit;

import java.util.Objects;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.transport.gemini.GeminiChatModel;

/**
 * Internal rate limiting wrapper for ChatModel.
 * Not part of the public API - users interact via LLM factory methods.
 * Spaces API calls evenly over time using a token bucket approach.
 */
public final class RateLimitedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final RateLimiter rateLimiter;

    RateLimitedChatModel(ChatModel delegate, RateLimiter rateLimiter) {
        this.delegate = Objects.requireNonNull(delegate);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    @Override
    public String chat(String input) {
    	try {
    		rateLimiter.acquire();
    	} catch (InterruptedException e) {
    		Thread.currentThread().interrupt();
    		throw new RuntimeException("Interrupted waiting for rate limit token", e);
    	}
        return delegate.chat(input);
    }

    // only LLM factory uses this
    public static RateLimitedChatModel of(ChatModel model, RateLimiter limiter) {
        return new RateLimitedChatModel(model, limiter);
    }
}