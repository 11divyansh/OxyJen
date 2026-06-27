package io.oxyjen.graph.cycles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.semantics.retry.RetryPolicy;

class RetryNodeTest {

    @Test
    void retriesTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();
        NodePlugin<String, String> flaky = new NodePlugin<>() {
            @Override
            public String process(String input, NodeContext context) {
                int current = attempts.incrementAndGet();
                if (current < 3) {
                    throw new IllegalStateException("transient");
                }
                return input + "-ok";
            }
        };

        RetryNode<String, String> retry = RetryNode.wrap(flaky)
                .policy(RetryPolicy.builder()
                        .maxAttempts(4)
                        .retryOn(IllegalStateException.class)
                        .build())
                .build("retry");

        String result = retry.process("input", new NodeContext());
        assertEquals("input-ok", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryNonRetryableFailures() {
        AtomicInteger attempts = new AtomicInteger();
        NodePlugin<String, String> bad = new NodePlugin<>() {
            @Override
            public String process(String input, NodeContext context) {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("bug");
            }
        };

        RetryNode<String, String> retry = RetryNode.wrap(bad)
                .policy(RetryPolicy.builder()
                        .maxAttempts(5)
                        .retryOn(IllegalStateException.class)
                        .build())
                .build("retry");

        assertThrows(IllegalArgumentException.class, () -> retry.process("input", new NodeContext()));
        assertEquals(1, attempts.get());
    }
}
