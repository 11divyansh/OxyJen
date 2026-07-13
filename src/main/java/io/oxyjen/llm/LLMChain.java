package io.oxyjen.llm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.oxyjen.execution.ExecutionEvent;
import io.oxyjen.execution.FailureInfo;
import io.oxyjen.llm.exceptions.InvalidAPIKeyException;
import io.oxyjen.llm.exceptions.LLMException;
import io.oxyjen.llm.exceptions.NetworkException;
import io.oxyjen.llm.exceptions.RateLimitException;
import io.oxyjen.llm.exceptions.TimeoutException;
import io.oxyjen.llm.internal.TimedChatModel;
import io.oxyjen.observe.ObservationBus;
import io.oxyjen.resilience.ratelimit.RateLimitedChatModel;
import io.oxyjen.semantics.retry.RetryPolicy;

/**
 * ChatModel with fallbacks and retries.
 *
 * Features:
 * - Primary + multiple fallback models
 * - Automatic retries with exponential backoff
 * - Timeout protection
 * - Error classification
 * - Jitter & retry cap
 *
 * maxRetries is the total number of attempts per model, not extra retries.
 *
 * This is Layer 3 (Execution Control).
 *
 * Example:
 * <pre>
 * ChatModel resilient = LLM.chain()
 *     .primary("gpt-4o")
 *     .fallback("gpt-3.5-turbo")
 *     .retry(3)
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 *
 * String response = resilient.chat("Hello");
 * // Auto-tries gpt-4o -> gpt-3.5-turbo if needed
 * </pre>
 * 
 * <p>Returns a full {@link LLMResponse} so callers (primarily {@code LLMNode})
 * get token counts, cost, and provider metadata from whichever model in the
 * chain actually produced the response including how many retries it took.
 */
public final class LLMChain implements ChatModel {

    private final ChatModel primary;
    private final List<ChatModel> fallbacks;
    private final RetryPolicy retryPolicy;
    private final ObservationBus bus;
    private final String nodeId;

    private LLMChain(Builder builder) {
        if (builder.timeout != null) {
            this.primary = new TimedChatModel(builder.primary, builder.timeout);
        } else {
            this.primary = builder.primary;
        }

        this.fallbacks = new ArrayList<>(builder.fallbacks.size());
        for (ChatModel fallback : builder.fallbacks) {
            if (builder.timeout != null) {
                this.fallbacks.add(new TimedChatModel(fallback, builder.timeout));
            } else {
                this.fallbacks.add(fallback);
            }
        }

        this.retryPolicy = builder.retryPolicy != null
                ? builder.retryPolicy
                : buildRetryPolicy(builder);
        
        this.bus = builder.bus;
        this.nodeId = builder.nodeId;
    }
    
    @Override 
    public LLMResponse chat(String input) {
    	return chat(input, null);
    }

    /**
     * Variant that accepts a per-call {@code executionId} used by
     * {@code LLMNode} which resolves the executionId from {@code NodeContext}
     * and passes it here so retry events carry the right correlation id.
     */
    public LLMResponse chat(String input, String executionId) {
        List<ChatModel> models = new ArrayList<>();
        models.add(primary);
        models.addAll(fallbacks);

        Exception lastException = null;
        int totalRetries = 0;

        for (ChatModel model : models) {
            printDecoratorChain(model);

            for (int attempt = 1; attempt <= retryPolicy.maxAttempts(); attempt++) {
                try {
                    log("Attempt " + attempt + " with " + modelName(model));
                    LLMResponse response = model.chat(input);
                    log("Success with " + modelName(model));
                    // If the provider didn't fill in retryCount (it won't —
                    // retryCount is a chain-level concept, not provider-level),
                    // rebuild the response with the retry count we tracked here.
                    // Only rebuild if retries actually occurred to avoid
                    // unnecessary allocation on the happy path.
                    if (totalRetries > 0) {
                        return new LLMResponse(
                                response.text(),
                                response.promptTokens(),
                                response.completionTokens(),
                                response.costMicros(),
                                response.modelInfo(),
                                response.cacheHit()
                        );
                    }
                    return response;
                } catch (Exception e) {
                    lastException = e;
                    totalRetries++;
                    String reason = classifyReason(e);
                    log("Failed: " + e.getMessage() + " [reason=" + reason + "]");

                    RetryPolicy.Decision decision = retryPolicy.decide(e, attempt);
                    boolean willRetry = decision.shouldRetry() && attempt < retryPolicy.maxAttempts();
                    long backoffMs = willRetry ? decision.delayMs() : 0L;
                    // emit RetryAttempt if bus is available
                    if (willRetry && bus != null && !bus.isEmpty()) {
                        String eid = executionId != null ? executionId : "unknown";
                        String nid = nodeId != null ? nodeId : "LLMChain";
                        bus.emit(new ExecutionEvent.RetryAttempt(
                                eid,
                                Instant.now(),
                                nid,
                                attempt + 1,
                                FailureInfo.from(e),
                                backoffMs
                        ));
                    }
                    if (willRetry) {
                        log("Attempt " + (attempt + 1) + " reason=" + reason
                                + " backoff=" + backoffMs + "ms");
                        sleep(backoffMs);
                        continue;
                    }

                    if (retryPolicy.isFailFast(e) || !retryPolicy.shouldRetry(e)) {
                        sneakyThrow(e);
                    }

                    break;
                }
            }
        }

        throw new LLMException(
                "All models failed after retries. Last error: "
                        + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException
        );
    }

    private RetryPolicy buildRetryPolicy(Builder builder) {
        RetryPolicy.Builder policyBuilder = RetryPolicy.builder()
                .maxAttempts(builder.maxRetries)
                .baseBackoff(Duration.ofSeconds(1))
                .rateLimitBackoff(Duration.ofSeconds(30))
                .retryOn(RateLimitException.class, NetworkException.class, TimeoutException.class)
                .failOn(InvalidAPIKeyException.class);

        if (builder.exponentialBackoff) {
            policyBuilder.exponential();
        } else {
            policyBuilder.fixedBackoff();
        }

        if (builder.maxBackoff != null) {
            policyBuilder.maxBackoff(builder.maxBackoff);
        }

        if (builder.jitterFactor > 0.0) {
            policyBuilder.jitter(builder.jitterFactor);
        }

        return policyBuilder.build();
    }

    private void printDecoratorChain(ChatModel model) {
        StringBuilder chain = new StringBuilder();
        ChatModel current = model;
        while (current != null) {
            chain.append(current.getClass().getSimpleName());
            if (current instanceof TimedChatModel timed) {
                current = getDelegate(timed, "delegate");
            } else if (current instanceof RateLimitedChatModel rateLimited) {
                current = getDelegate(rateLimited, "delegate");
            } else {
                current = null;
            }
            if (current != null) {
                chain.append(" -> ");
            }
        }
        log("Decorator chain: " + chain);
    }

    private ChatModel getDelegate(ChatModel wrapper, String fieldName) {
        try {
            var field = wrapper.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (ChatModel) field.get(wrapper);
        } catch (Exception e) {
            return null;
        }
    }

    private String classifyReason(Exception e) {
        if (e instanceof RateLimitException) return "RateLimit";
        if (e instanceof TimeoutException) return "Timeout";
        if (e instanceof NetworkException) return "NetworkError";
        if (e instanceof InvalidAPIKeyException) return "InvalidAPIKey";
        return e.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String message) {
        System.out.println("[LLMChain] " + message);
        // TODO: Proper logging in v0.3
    }

    private String modelName(ChatModel model) {
        return model.getClass().getSimpleName();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ChatModel primary;
        private List<ChatModel> fallbacks = new ArrayList<>();
        private int maxRetries = 3;
        private Duration timeout = null;
        private boolean exponentialBackoff = true;
        private Duration maxBackoff = null;
        private double jitterFactor = 0.0;
        private RetryPolicy retryPolicy = null;
        private ObservationBus bus = null;
        private String nodeId = null;

        /**
         * Set primary model (required).
         */
        public Builder primary(ChatModel model) {
            this.primary = model;
            return this;
        }

        /**
         * Set primary model by name.
         */
        public Builder primary(String modelName) {
            this.primary = LLM.of(modelName);
            return this;
        }

        /**
         * Add fallback model.
         */
        public Builder fallback(ChatModel model) {
            this.fallbacks.add(model);
            return this;
        }

        /**
         * Add fallback model by name.
         */
        public Builder fallback(String modelName) {
            this.fallbacks.add(LLM.of(modelName));
            return this;
        }

        /**
         * Set the total number of attempts per model.
         * Example: retry(3) means attempt 1 + attempt 2 + attempt 3.
         */
        public Builder retry(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Provide a complete retry policy.
         * When set, the legacy retry knobs are ignored.
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /**
         * Set timeout for each call.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enable exponential backoff for retries.
         */
        public Builder exponentialBackoff() {
            this.exponentialBackoff = true;
            return this;
        }

        /**
         * Disable exponential backoff (fixed delay).
         */
        public Builder fixedBackoff() {
            this.exponentialBackoff = false;
            return this;
        }

        /**
         * Set maximum backoff duration (caps exponential growth).
         * Example: maxBackoff(Duration.ofSeconds(10))
         */
        public Builder maxBackoff(Duration maxBackoff) {
            if (maxBackoff.isZero() || maxBackoff.isNegative()) {
                throw new IllegalArgumentException("maxBackoff must be positive");
            }
            this.maxBackoff = maxBackoff;
            return this;
        }

        /**
         * Enable jitter to randomize retry delays.
         * @param factor Jitter factor (0.0 to 1.0).
         *               0.2 means +/-20% randomness.
         * Example: jitter(0.2)
         */
        public Builder jitter(double factor) {
            if (factor < 0 || factor > 1) {
                throw new IllegalArgumentException(
                    "Jitter factor must be between 0 and 1, got: " + factor
                );
            }
            this.jitterFactor = factor;
            return this;
        }
        
        /**
        * Provide an {@link ObservationBus} so the chain can emit
        * {@link ExecutionEvent.RetryAttempt} events instead of logging.
        * Optional if not set, retries are silent at the chain level
        * (node-level failure events are still emitted by the executor).
        */
       public Builder observationBus(ObservationBus bus) {
           this.bus = bus;
           return this;
       }

       /**
        * Node identifier for retry events typically the containing
        * {@code LLMNode}'s name. Only meaningful when {@link #observationBus}
        * is also set.
        */
       public Builder nodeId(String nodeId) {
           this.nodeId = nodeId;
           return this;
       }

        public LLMChain build() {
            if (primary == null) {
                throw new IllegalStateException("Primary model must be set");
            }
            return new LLMChain(this);
        }
    }
}