package io.oxyjen.core.graphs;

import java.util.concurrent.atomic.AtomicInteger;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class Nodes {
	static class InputNode implements NodePlugin<String, String> {
        @Override
        public String process(String input, NodeContext context) {
            return input;
        }
    }
    static class UppercaseNode implements NodePlugin<String, String> {
        @Override
        public String process(String input, NodeContext context) {
            return input.toUpperCase();
        }
    }

    static class AppendNode implements NodePlugin<String, String> {
        private final String suffix;
        AppendNode(String suffix) {
            this.suffix = suffix;
        }
        @Override
        public String process(String input, NodeContext context) {
            return input + suffix;
        }
    }

    static class FailingNode implements NodePlugin<String, String> {
        @Override
        public String process(String input, NodeContext context) {
            throw new RuntimeException("Failure node triggered");
        }
    }

    static class CountingNode implements NodePlugin<Integer, Integer> {
        AtomicInteger counter = new AtomicInteger();
        @Override
        public Integer process(Integer input, NodeContext context) {
            return counter.incrementAndGet();
        }
    }
}