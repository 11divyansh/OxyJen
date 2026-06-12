package io.oxyjen.llm.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import io.oxyjen.llm.ChatModel;

/**
 * Internal rate limiting wrapper for ChatModel.
 * Not part of the public API - users interact via LLM factory methods.
 * Spaces API calls evenly over time using a token bucket approach.
 */
public final class RateLimitedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final long intervalMs;
    private final AtomicLong lastCallTime = new AtomicLong(0);
    private final Object lock = new Object();

    RateLimitedChatModel(ChatModel delegate, int requestsPerMinute) {
        if (requestsPerMinute <= 0)
            throw new IllegalArgumentException("requestsPerMinute must be > 0");
        this.delegate = Objects.requireNonNull(delegate);
        this.intervalMs = 60_000L / requestsPerMinute;
    }

    @Override
    public String chat(String input) {
        throttle();
        return delegate.chat(input);
    }

    private void throttle() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastCallTime.get();
            if (elapsed < intervalMs) {
                try {
                    Thread.sleep(intervalMs - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallTime.set(System.currentTimeMillis());
        }
    }

    // only LLM factory uses this
    public static RateLimitedChatModel of(ChatModel model, int rpm) {
        return new RateLimitedChatModel(model, rpm);
    }
}