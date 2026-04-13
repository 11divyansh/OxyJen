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
public class BranchNode<I> implements NodePlugin<I, Object> {

	public record RoutedResult(String nextNode, Object output) {}

    private record Branch<I>(
        String name,
        Predicate<I> predicate,
        Function<I, Object> transform,
        String nextNode
    ) {}

    private final String name;
    private final List<Branch<I>> branches;
    private final Function<I, Object> elseBranch;    
    private final String elseNextNode;

    private BranchNode(
            String name,
            List<Branch<I>> branches,
            Function<I, Object> elseBranch,
            String elseNextNode
    ) {
        this.name = Objects.requireNonNull(name);
        this.branches = List.copyOf(branches);
        this.elseBranch = elseBranch;
        this.elseNextNode = elseNextNode;
    }

    @Override
    public Object process(I input, NodeContext context) {
        for (Branch<I> branch : branches) {
            if (branch.predicate().test(input)) {
                Object output = branch.transform().apply(input);
                incrementMetric(context, branch.name);
                context.getLogger().info(
                    "[BranchNode:" + name + "] -> " + branch.name()
                );
                return new RoutedResult(branch.nextNode, output);
            }
        }

        // no branch matched
        if (elseBranch != null) {
            Object output = elseBranch.apply(input);
            context.getLogger().info(
                "[BranchNode:" + name + "] No branch matched, using else");
            return new RoutedResult(elseNextNode, output);
        }

        throw new NoBranchMatchedException(name, input);
    }
    private void incrementMetric(NodeContext ctx, String branch) {
        String key = "branch." + name + "." + branch + ".count";
        Integer count = ctx.get(key, Integer.class).orElse(0);
        ctx.set(key, count + 1);
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
        private Function<I, Object> elseBranch;
        private String elseNext;

        @SuppressWarnings("unchecked")
        public Builder<I> when(String name, Predicate<I> predicate) {
            return branch(name, predicate, (Function<I, Object>) Function.identity(), null);
        }

        public Builder<I> when(
                String name,
                Predicate<I> predicate,
                Function<I, Object> transform
        ) {
            return branch(name, predicate, transform, null);
        }

        // internal
        private Builder<I> branch(
                String name,
                Predicate<I> predicate,
                Function<I, Object> transform,
                String nextNode
        ) {
            branches.add(new Branch<>(name, predicate, transform, nextNode));
            return this;
        }

        public Builder<I> then(String nextNode) {
            Branch<I> last = branches.remove(branches.size() - 1);
            branches.add(new Branch<>(last.name, last.predicate, last.transform, nextNode));
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder<I> orElse(String nextNode) {
            this.elseNext = nextNode;
            this.elseBranch = (Function<I, Object>) Function.identity();
            return this;
        }

        public BranchNode<I> build(String name) {
            return new BranchNode<>(name, branches, elseBranch, elseNext);
        }
    }

    public static class NoBranchMatchedException extends RuntimeException {
        public NoBranchMatchedException(String nodeName, Object input) {
            super("BranchNode [" + nodeName + "] found no matching branch for input: "
                + (input == null ? "null" : input.getClass().getSimpleName()));
        }
    }
}