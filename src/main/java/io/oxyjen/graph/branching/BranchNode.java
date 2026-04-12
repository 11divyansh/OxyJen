package io.oxyjen.graph.branching;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * A node that implements mutually exclusive conditional branching (if / else-if / else).
 *
 * BranchNode evaluates a list of named branches in order and selects the first one
 * whose predicate returns true. Only that branch's output is returned. All other
 * branches are skipped. If no branch matches, the else branch (if configured) is used,
 * or a {@link NoBranchMatchedException} is thrown.
 *
 * @param <I> Input type this node receives.
 */
public class BranchNode<I> implements NodePlugin<I, BranchNode.BranchResult> {

    /**
     * Wraps the selected branch name and transformed output.
     * Stored in context under the node's name so downstream ConditionalEdges can route.
     */
    public record BranchResult(String branchName, Object output) {
        /** Convenience check for ConditionalEdge predicates. */
        public boolean is(String name) {
            return branchName.equals(name);
        }

        @SuppressWarnings("unchecked")
        public <T> T outputAs() {
            return (T) output;
        }
    }

    private record Branch<I>(
        String name,
        Predicate<I> predicate,
        Function<I, Object> transform
    ) {}

    private final String name;
    private final List<Branch<I>> branches;
    private final Function<I, Object> elseBranch;    
    private final String elseBranchName;

    private BranchNode(
            String name,
            List<Branch<I>> branches,
            Function<I, Object> elseBranch,
            String elseBranchName
    ) {
        this.name = Objects.requireNonNull(name);
        this.branches = List.copyOf(branches);
        this.elseBranch = elseBranch;
        this.elseBranchName = elseBranchName;
    }

    @Override
    public BranchResult process(I input, NodeContext context) {
        for (Branch<I> branch : branches) {
            if (branch.predicate().test(input)) {
                Object output = branch.transform().apply(input);
                BranchResult result = new BranchResult(branch.name(), output);
                context.set(name + ".branch", result);
                context.getLogger().info(
                    "[BranchNode:" + name + "] Selected branch: " + branch.name()
                );
                return result;
            }
        }

        // no branch matched
        if (elseBranch != null) {
            Object output = elseBranch.apply(input);
            BranchResult result = new BranchResult(elseBranchName, output);
            context.set(name + ".branch", result);
            context.getLogger().info(
                "[BranchNode:" + name + "] No branch matched, using else: " + elseBranchName
            );
            return result;
        }

        throw new NoBranchMatchedException(name, input);
    }

    @Override
    public String getName() {
        return name;
    }

    public static <I> Builder<I> builder() {
        return new Builder<>();
    }

    public static final class Builder<I> {

        private final List<Branch<I>> branches = new ArrayList<>();
        private Function<I, Object> elseBranch = null;
        private String elseBranchName = "else";

        /**
         * Add a named branch with a predicate and an identity transform (pass-through).
         */
        public Builder<I> branch(String name, Predicate<I> predicate) {
            return branch(name, predicate, input -> input);
        }

        /**
         * Add a named branch with a predicate and a value transform.
         * Branches are evaluated in insertion order - first match wins.
         */
        public Builder<I> branch(
                String name,
                Predicate<I> predicate,
                Function<I, Object> transform
        ) {
            Objects.requireNonNull(name, "Branch name must not be null");
            Objects.requireNonNull(predicate, "Branch predicate must not be null");
            Objects.requireNonNull(transform, "Branch transform must not be null");
            branches.add(new Branch<>(name, predicate, transform));
            return this;
        }

        /**
         * Add a catch-all else branch. If no other branch matches, this runs.
         * The else branch is always a pass-through of the input value.
         */
        public Builder<I> orElse(String branchName) {
            return orElse(branchName, input -> input);
        }

        /**
         * Add a catch-all else branch with a custom transform.
         */
        public Builder<I> orElse(String branchName, Function<I, Object> transform) {
            this.elseBranchName = Objects.requireNonNull(branchName);
            this.elseBranch = Objects.requireNonNull(transform);
            return this;
        }

        /**
         * If no branch matches, throw {@link NoBranchMatchedException} (default behaviour).
         * Calling this is optional - it's the default when orElse() is not called.
         */
        public Builder<I> orElseThrow() {
            this.elseBranch = null;
            return this;
        }

        public BranchNode<I> build(String nodeName) {
            if (branches.isEmpty()) {
                throw new IllegalStateException("BranchNode [" + nodeName + "] must have at least one branch");
            }
            return new BranchNode<>(nodeName, branches, elseBranch, elseBranchName);
        }
    }

    public static class NoBranchMatchedException extends RuntimeException {
        public NoBranchMatchedException(String nodeName, Object input) {
            super("BranchNode [" + nodeName + "] found no matching branch for input: "
                + (input == null ? "null" : input.getClass().getSimpleName()));
        }
    }
}