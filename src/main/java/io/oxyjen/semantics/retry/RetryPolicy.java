package io.oxyjen.semantics.retry;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import io.oxyjen.llm.exceptions.RateLimitException;
import io.oxyjen.llm.exceptions.TimeoutException;

/**
 * Retry policy for graph-level retry wrappers.
 *
 * Keep this separate from {@code LLMChain} retries:
 * - LLMChain owns model/fallback/backoff behavior for chat calls.
 * - RetryPolicy owns generic node retries in graph semantics.
 */
public final class RetryPolicy {

    /**
     * maxAttempts is the total number of tries, not the number of retries.
     * Example: maxAttempts=3 means attempt 1 + attempt 2 + attempt 3.
     */
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final boolean exponential;
    private final double jitterFactor;
    private final Set<Class<? extends Throwable>> retryables;
    private final Set<Class<? extends Throwable>> failFast;

    private RetryPolicy(Builder builder) {
        this.maxAttempts = builder.maxAttempts;
        this.baseBackoffMs = builder.baseBackoffMs;
        this.maxBackoffMs = builder.maxBackoffMs;
        this.exponential = builder.exponential;
        this.jitterFactor = builder.jitterFactor;
        this.retryables = Collections.unmodifiableSet(new LinkedHashSet<>(builder.retryables));
        this.failFast = Collections.unmodifiableSet(new LinkedHashSet<>(builder.failFast));
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder exponential() {
        return builder().exponential();
    }

    public static RetryPolicy defaultPolicy() {
        return builder()
                .retryOn(
                        RateLimitException.class,
                        TimeoutException.class,
                        java.util.concurrent.TimeoutException.class,
                        java.net.http.HttpTimeoutException.class,
                        java.net.ConnectException.class,
                        java.net.SocketTimeoutException.class
                )
                .failOn(IllegalArgumentException.class, IllegalStateException.class)
                .build();
    }

    /**
     * Evaluate a failure and decide whether to retry plus how long to wait.
     */
    public Decision decide(Throwable failure, int attempt) {
        if (failure == null) {
            return Decision.noRetry();
        }
        if (attempt >= maxAttempts) {
            return Decision.noRetry();
        }
        if (!shouldRetry(failure)) {
            return Decision.noRetry();
        }

        long delayMs = retryAfterMs(failure);
        if (delayMs <= 0) {
            delayMs = calculateBackoff(failure, attempt);
        }
        return Decision.retry(delayMs);
    }

    public boolean shouldRetry(Throwable failure) {
        if (failure == null) {
            return false;
        }
        if (failure instanceof Error) {
            return false;
        }
        if (isFailFast(failure)) {
            return false;
        }
        if (retryables.isEmpty()) {
            return false;
        }
        for (Class<? extends Throwable> type : retryables) {
            if (type.isAssignableFrom(failure.getClass())) {
                return true;
            }
        }
        return false;
    }

    public long calculateBackoff(int attempt) {
        return calculateBackoff(null, attempt);
    }

    public long calculateBackoff(Throwable failure, int attempt) {
        if (attempt < 1) {
            return 0L;
        }
        long delay = exponential
                ? saturatingMultiply(baseBackoffMs, 1L << Math.min(Math.max(0, attempt - 1), 30))
                : baseBackoffMs;
        if (maxBackoffMs > 0) {
            delay = Math.min(delay, maxBackoffMs);
        }
        if (jitterFactor > 0.0) {
            double multiplier = ThreadLocalRandom.current()
                    .nextDouble(1.0 - jitterFactor, 1.0 + jitterFactor);
            delay = Math.max(0L, (long) (delay * multiplier));
        }
        return delay;
    }

    private long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    public boolean isFailFast(Throwable failure) {
        if (failure == null) {
            return false;
        }
        for (Class<? extends Throwable> type : failFast) {
            if (type.isAssignableFrom(failure.getClass())) {
                return true;
            }
        }
        return false;
    }

    public long retryAfterMs(Throwable failure) {
        if (failure instanceof RateLimitException rle && rle.hasRetryAfter()) {
            return rle.getRetryAfterMs();
        }
        return 0L;
    }

    public static final class Decision {
        private static final Decision NO_RETRY = new Decision(false, 0L);

        private final boolean shouldRetry;
        private final long delayMs;

        private Decision(boolean shouldRetry, long delayMs) {
            this.shouldRetry = shouldRetry;
            this.delayMs = delayMs;
        }

        public static Decision retry(long delayMs) {
            return new Decision(true, Math.max(0L, delayMs));
        }

        public static Decision noRetry() {
            return NO_RETRY;
        }

        public boolean shouldRetry() {
            return shouldRetry;
        }

        public long delayMs() {
            return delayMs;
        }
    }

    public static final class Builder {
        private int maxAttempts = 3;
        private long baseBackoffMs = Duration.ofMillis(250).toMillis();
        private long maxBackoffMs = Duration.ofSeconds(30).toMillis();
        private boolean exponential = true;
        private double jitterFactor = 0.0;
        private final Set<Class<? extends Throwable>> retryables = new LinkedHashSet<>();
        private final Set<Class<? extends Throwable>> failFast = new LinkedHashSet<>();

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder baseBackoff(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("baseBackoff must be non-negative");
            }
            this.baseBackoffMs = duration.toMillis();
            return this;
        }

        public Builder initialDelay(Duration duration) {
            return baseBackoff(duration);
        }

        public Builder maxBackoff(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("maxBackoff must be non-negative");
            }
            this.maxBackoffMs = duration.toMillis();
            return this;
        }

        public Builder maxDelay(Duration duration) {
            return maxBackoff(duration);
        }

        /**
         * Backward-compatible alias for baseBackoff.
         * Rate-limit retries now use the same backoff path as other transient failures.
         */
        @Deprecated
        public Builder rateLimitBackoff(Duration duration) {
            return baseBackoff(duration);
        }

        public Builder exponential() {
            this.exponential = true;
            return this;
        }

        public Builder exponentialBackoff(boolean enabled) {
            this.exponential = enabled;
            return this;
        }

        public Builder fixedBackoff() {
            this.exponential = false;
            return this;
        }

        public Builder jitter(double factor) {
            if (factor < 0.0 || factor > 1.0) {
                throw new IllegalArgumentException("jitter must be between 0.0 and 1.0");
            }
            this.jitterFactor = factor;
            return this;
        }

        @SafeVarargs
        public final Builder retryOn(Class<? extends Throwable>... types) {
            if (types == null) {
                return this;
            }
            for (Class<? extends Throwable> type : types) {
                if (type != null) {
                    retryables.add(type);
                }
            }
            return this;
        }

        @SafeVarargs
        public final Builder failOn(Class<? extends Throwable>... types) {
            if (types == null) {
                return this;
            }
            for (Class<? extends Throwable> type : types) {
                if (type != null) {
                    failFast.add(type);
                }
            }
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
