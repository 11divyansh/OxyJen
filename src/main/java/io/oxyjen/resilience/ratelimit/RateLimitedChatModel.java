package io.oxyjen.resilience.ratelimit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLMResponse;
import io.oxyjen.llm.exceptions.RateLimitException;

/**
 * Internal rate limiting wrapper for ChatModel.
 * Not part of the public API - users interact via LLM factory methods.
 * Spaces API calls evenly over time using a token bucket approach.
 */
public final class RateLimitedChatModel implements ChatModel {

    private static final long DEFAULT_429_COOLDOWN_MS = 30_000L;

    private final ChatModel delegate;
    private final RateLimiter rateLimiter;
    private final AtomicInteger permitCounter = new AtomicInteger(0);

    RateLimitedChatModel(ChatModel delegate, RateLimiter rateLimiter) {
        this.delegate = Objects.requireNonNull(delegate);
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
    }

    @Override
    public LLMResponse chat(String input) {
    	String threadName = Thread.currentThread().getName();
    	long requestStart = System.currentTimeMillis();
    	System.out.println("[RateLimiter] " + threadName + " requesting permit");
    	try {
    		rateLimiter.acquire();
    	} catch (InterruptedException e) {
    		Thread.currentThread().interrupt();
    		throw new RuntimeException("Interrupted waiting for rate limit token", e);
    	}
    	long waitedMs = System.currentTimeMillis() - requestStart;
        int permitNum = permitCounter.incrementAndGet();

        System.out.println(
            "[RateLimiter] " + threadName
            + " acquired permit #" + permitNum
            + " after " + waitedMs + "ms wait"
        );

        try {
            return delegate.chat(input);
        } catch (RateLimitException e) {
        	// tell the adaptive limiter about the block
            if (rateLimiter instanceof AdaptiveRateLimiter adaptive) {
                adaptive.on429(e.hasRetryAfter() ? e.getRetryAfterMs() : DEFAULT_429_COOLDOWN_MS);
            }
            throw e; // re-throw so LLMChain handles retry
        }
        finally {
            System.out.println(
                "[RateLimiter] permit #" + permitNum
                + " released by " + threadName
            );
        }
    }

    // only LLM factory uses this
    public static RateLimitedChatModel of(ChatModel model, RateLimiter limiter) {
        return new RateLimitedChatModel(model, limiter);
    }
}