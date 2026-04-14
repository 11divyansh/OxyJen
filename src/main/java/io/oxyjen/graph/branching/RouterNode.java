package io.oxyjen.graph.branching;

import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

import java.util.*;
import java.util.function.Predicate;

/**
 * RouterNode performs Multi-branch routing (fan-out).
 *
 * Unlike BranchNode (single-path), RouterNode:
 * - Evaluates all routes
 * - Fires all matching routes
 * - Can trigger multiple downstream nodes in parallel
 *
 * This node returns a RoutedResult, which the executor uses to
 * directly schedule downstream nodes (no ConditionalEdge required).
 *
 * Example:
 *
 * RouterNode<String> router = RouterNode.<String>builder()
 *     .route("hasA", s -> s.contains("a"), "NodeA")
 *     .route("hasB", s -> s.contains("b"), "NodeB")
 *     .build("router");
 *
 * Input: "abc"
 * → routes to BOTH NodeA and NodeB
 */
public class RouterNode<I> implements NodePlugin<I, Object> {

    /**
     * Output returned by RouterNode.
     * Represents all downstream routes to execute.
     *
     * Map:
     *   nextNodeName -> transformedOutput
     */
    public record RoutedResult(Map<String, Object> routes) {}

    /**
     * Internal route definition.
     */
    private record Route<I>(
            String name,
            Predicate<I> predicate,
            Function<I, Object> transform,
            String nextNode
    ) {}

    private final String name;
    private final List<Route<I>> routes;
    private final boolean requireAtLeastOne;

    private RouterNode(
            String name,
            List<Route<I>> routes,
            boolean requireAtLeastOne
    ) {
        this.name = Objects.requireNonNull(name);
        this.routes = List.copyOf(routes);
        this.requireAtLeastOne = requireAtLeastOne;
    }

    /**
     * Executes routing logic.
     *
     * - Evaluates ALL routes
     * - Collects all matching routes
     * - Returns RoutedResult for executor to handle fan-out
     */
    @Override
    public Object process(I input, NodeContext context) {
        Map<String, Object> fired = new LinkedHashMap<>();
        for (Route<I> route : routes) {
            boolean matches;
            try {
                matches = route.predicate().test(input);
            } catch (Exception e) {
                context.getLogger().warning(
                        "[RouterNode:" + name + "] Predicate failed for route '" + route.name + "'"
                );
                matches = false;
            }
            if (matches) {
                Object output = route.transform().apply(input);
                fired.put(route.nextNode, output);
                context.getLogger().info(
                        "[RouterNode:" + name + "] Route fired: " + route.name
                );
            }
        }
        if (requireAtLeastOne && fired.isEmpty()) {
            throw new NoRouteFiredException(name, input);
        }
        if (fired.isEmpty()) {
            context.getLogger().warning(
                    "[RouterNode:" + name + "] No routes fired (dead end)"
            );
        }
        return new RoutedResult(fired);
    }

    @Override
    public String getName() {
        return name;
    }

    public static <I> Builder<I> builder() {
        return new Builder<>();
    }

    public static final class Builder<I> {
        private final List<Route<I>> routes = new ArrayList<>();
        private boolean requireAtLeastOne = false;

        /**
         * Add a route with identity transform.
         */
        @SuppressWarnings("unchecked")
		public Builder<I> route(
                String name,
                Predicate<I> predicate,
                String nextNode
        ) {
            return route(name, predicate, (Function<I, Object>) Function.identity(), nextNode);
        }

        /**
         * Add a route with transform.
         *
         * @param name       logical route name (for logs/debugging)
         * @param predicate  condition
         * @param transform  transforms input before passing to next node
         * @param nextNode   target node name
         */
        public Builder<I> route(
                String name,
                Predicate<I> predicate,
                Function<I, Object> transform,
                String nextNode
        ) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(predicate);
            Objects.requireNonNull(transform);
            Objects.requireNonNull(nextNode);
            routes.add(new Route<>(name, predicate, transform, nextNode));
            return this;
        }

        /**
         * Enforce that at least one route must fire.
         */
        public Builder<I> requireAtLeastOne() {
            this.requireAtLeastOne = true;
            return this;
        }

        public RouterNode<I> build(String name) {
            if (routes.isEmpty()) {
                throw new IllegalStateException("RouterNode must have at least one route");
            }
            return new RouterNode<>(name, routes, requireAtLeastOne);
        }
    }
    public static class NoRouteFiredException extends RuntimeException {
        public NoRouteFiredException(String nodeName, Object input) {
            super("RouterNode [" + nodeName + "] no routes matched for input: "
                    + (input == null ? "null" : input.getClass().getSimpleName()));
        }
    }
}