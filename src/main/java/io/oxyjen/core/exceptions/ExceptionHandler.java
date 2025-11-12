package io.oxyjen.core.exceptions;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public interface ExceptionHandler {
	void handleException(NodePlugin<?, ?> node, Exception e, NodeContext context);

    static ExceptionHandler defaultHandler() {
        return (node, e, ctx) -> {
            ctx.getLogger().severe("Error in node [" + node.getName() + "]: " + e.getMessage());
            throw new RuntimeException(e);
        };
    }
}
