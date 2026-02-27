package io.oxyjen.llm.execution;

import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
/**
 * The RouterNode decides, for a given input which path/node/tool should run next.
 * @param <I>
 * @param <O>
 */
public final class RouterNode<I, O> implements NodePlugin<I, O> {
	private final Function<I,O> router;

    public RouterNode(Function<I,O> router) {
        this.router = router;
    }

    @Override
    public O process(I input, NodeContext ctx) {
        return router.apply(input);
    }
}
