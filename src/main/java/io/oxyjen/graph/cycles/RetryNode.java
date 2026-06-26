package io.oxyjen.graph.cycles;

import java.util.Objects;
import java.util.concurrent.CancellationException;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.semantics.retry.RetryPolicy;

/**
 * Wraps any NodePlugin with retry semantics at the graph level.
 *
 * Unlike LLMChain which retries LLM API calls specifically,
 * RetryNode retries ANY NodePlugin execution - HTTP nodes,
 * database nodes, custom integrations, or any transiently failing node.
 *
 * Each retry attempt gets an isolated child context so attempt
 * metadata doesn't bleed between attempts.
 *
 * WARNING: Do NOT wrap LLMNode or SchemaNode with RetryNode unless
 * you disable LLMChain's internal retry first. Stacking retries multiplies
 * attempts: RetryNode(3) × LLMChain(3) = up to 9 actual API calls.
 * RetryNode is intended for nodes WITHOUT their own retry semantics.
 *
 * maxAttempts is the total number of tries, not the number of retries.
 *
 * Usage:
 * <pre>
 * RetryNode.wrap(myHttpNode)
 *     .policy(RetryPolicy.exponential()
 *         .maxAttempts(3)
 *         .baseBackoff(Duration.ofSeconds(2))
 *         .maxBackoff(Duration.ofSeconds(30))
 *         .jitter(0.15)
 *         .retryOn(IOException.class, TimeoutException.class)
 *         .failOn(InvalidAPIKeyException.class)
 *         .build())
 *     .listener(new MyRetryListener())
 *     .build("retryExtract");
 * </pre>
 */
public final class RetryNode<I, O> implements NodePlugin<I, O> {

    private final NodePlugin<I, O> delegate;
    private final RetryPolicy policy;
    private final RetryListener listener;
    private final String name;

    private RetryNode(Builder<I, O> builder) {
        this.delegate = builder.delegate;
        this.policy   = builder.policy;
        this.listener = builder.listener != null
            ? builder.listener
            : RetryListener.noOp();
        this.name = builder.name;
    }

    @Override
    public O process(I input, NodeContext context) {
        Throwable lastException = null;
        int maxAttempts = policy.maxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // Isolated child context per attempt
            NodeContext attemptContext = context.child(name + "/attempt-" + attempt);
            attemptContext.setMetadata("retryAttempt", attempt);
            attemptContext.setMetadata("retryMaxAttempts", maxAttempts);
            attemptContext.setMetadata("retryNodeName", name);

            try {
                context.getLogger().info(
                    "[RetryNode:" + name + "] Attempt " + attempt + "/" + maxAttempts
                );

                O result = delegate.process(input, attemptContext);

                if (attempt > 1) {
                    context.getLogger().info(
                        "[RetryNode:" + name + "] Succeeded on attempt " + attempt
                    );
                    listener.onRecovery(new RetryEvent(name, attempt, maxAttempts, null, attemptContext));
                }
                return result;

            } catch (Exception e) {
                lastException = e;
                RetryEvent event = RetryEvent.of(name, attempt, maxAttempts, e, attemptContext);
                context.getLogger().warning(
                    "[RetryNode:" + name + "] Attempt " + attempt + " failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                );

                listener.onFailure(event);
                RetryPolicy.Decision decision = policy.decide(e, attempt);

                if (!decision.shouldRetry()) {
                    context.getLogger().severe(
                        "[RetryNode:" + name + "] Policy says stop retrying: "
                        + e.getClass().getSimpleName()
                    );
                    sneakyThrow(e);
                    return null;
                }
                if (attempt == maxAttempts) break;

                long backoffMs = decision.delayMs() > 0
                    ? decision.delayMs()
                    : policy.calculateBackoff(attempt);

                context.getLogger().info(
                    "[RetryNode:" + name + "] Retrying in " + backoffMs
                    + "ms (attempt " + (attempt + 1) + "/" + maxAttempts + ")"
                );
                listener.onRetry(event);
                sleep(backoffMs, context);
            }
        }
        throw new RetryExhaustedException(name, maxAttempts, lastException);
    }

    /**
     * Cancellation-aware sleep.
     * Graph cancellation interrupts the thread, stop retrying immediately.
     */
    private void sleep(long ms, NodeContext context) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getLogger().warning(
                "[RetryNode:" + name + "] Sleep interrupted — cancelling retries"
            );
            throw new CancellationException(
                "RetryNode [" + name + "] cancelled during backoff sleep"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    @Override
    public String getName() { return name; }

    public static <I, O> Builder<I, O> wrap(NodePlugin<I, O> node) {
        return new Builder<>(node);
    }

    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    /**
     * Carries all retry context for listeners and predicates.
     */
    public static final class RetryEvent {

        private final String nodeName;
        private final int attempt;
        private final int maxAttempts;
        private final Throwable exception;
        private final NodeContext context;

        private RetryEvent(
                String nodeName,
                int attempt,
                int maxAttempts,
                Throwable exception,
                NodeContext context
        ) {
            this.nodeName   = nodeName;
            this.attempt    = attempt;
            this.maxAttempts = maxAttempts;
            this.exception  = exception;
            this.context    = context;
        }

        public static RetryEvent of(
                String nodeName, int attempt, int maxAttempts,
                Throwable exception, NodeContext context
        ) {
            return new RetryEvent(nodeName, attempt, maxAttempts, exception, context);
        }

        public String nodeName()    { return nodeName; }
        public int attempt()        { return attempt; }
        public int maxAttempts()    { return maxAttempts; }
        public Throwable exception(){ return exception; }
        public NodeContext context() { return context; }
        public boolean isFirst()    { return attempt == 1; }
        public boolean isLast()     { return attempt == maxAttempts; }

        @Override
        public String toString() {
            return "RetryEvent[node=" + nodeName + ", attempt=" + attempt
                + "/" + maxAttempts + ", error="
                + (exception != null ? exception.getClass().getSimpleName() : "none") + "]";
        }
    }

    /**
     * Listener for retry lifecycle events.
     * Use for metrics, tracing, alerting, Prometheus counters.
     *
     * <pre>
     * .listener(new RetryListener() {
     *     public void onRetry(RetryEvent e) {
     *         metrics.increment("retry.count", "node", e.nodeName());
     *     }
     *     public void onSuccess(RetryEvent e) {
     *         metrics.record("retry.recovered", e.attempt());
     *     }
     *     public void onFailure(RetryEvent e) {
     *         metrics.increment("retry.failure", "node", e.nodeName());
     *     }
     * })
     * </pre>
     */
    public interface RetryListener {

        /** Called before each retry sleep (attempt failed, will retry). */
        void onRetry(RetryEvent event);

        /** Called when a retry attempt recovers after previous failures. */
        default void onRecovery(RetryEvent event) {
            onSuccess(event);
        }

        /** Backward-compatible alias. Prefer onRecovery in new code. */
        default void onSuccess(RetryEvent event) {}

        /** Called on every failed attempt (including last). */
        void onFailure(RetryEvent event);

        static RetryListener noOp() {
            return new RetryListener() {
                public void onRetry(RetryEvent e) {}
                public void onRecovery(RetryEvent e) {}
                public void onSuccess(RetryEvent e) {}
                public void onFailure(RetryEvent e) {}
            };
        }
    }

    /**
     * Thrown when all retry attempts are exhausted.
     * Use getCause() to access the original exception type.
     */
    public static final class RetryExhaustedException extends RuntimeException {

        private final String nodeName;
        private final int attempts;

        public RetryExhaustedException(String nodeName, int attempts, Throwable cause) {
            super(
                "RetryNode [" + nodeName + "] stopped after " + attempts + " attempt(s). "
                + "Last error: " + (cause != null ? cause.getMessage() : "unknown"),
                cause
            );
            this.nodeName = nodeName;
            this.attempts = attempts;
        }

        public String getNodeName() { return nodeName; }
        public int getAttempts()    { return attempts; }
    }

    public static final class Builder<I, O> {

        private NodePlugin<I, O> delegate;
        private RetryPolicy policy = RetryPolicy.defaultPolicy();
        private RetryListener listener = null;
        private String name;

        private Builder() {
        }

        private Builder(NodePlugin<I, O> delegate) {
            delegate(delegate);
        }

        public Builder<I, O> delegate(NodePlugin<I, O> delegate) {
            this.delegate = Objects.requireNonNull(delegate);
            this.name = "retry[" + delegate.getName() + "]";
            return this;
        }

        public Builder<I, O> policy(RetryPolicy policy) {
            this.policy = Objects.requireNonNull(policy);
            return this;
        }

        public Builder<I, O> listener(RetryListener listener) {
            this.listener = Objects.requireNonNull(listener);
            return this;
        }

        public RetryNode<I, O> build(String nodeName) {
            if (delegate == null) {
                throw new IllegalStateException("delegate must be set before build()");
            }
            this.name = Objects.requireNonNull(nodeName);
            return new RetryNode<>(this);
        }
    }
}
