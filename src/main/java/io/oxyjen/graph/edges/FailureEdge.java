package io.oxyjen.graph.edges;

import io.oxyjen.core.Edge;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class FailureEdge extends Edge {

	 public FailureEdge(NodePlugin<?, ?> source, NodePlugin<?, ?> target) {
	        super(source, target);
	    }

	    @Override
	    public boolean shouldTraverse(Object output, NodeContext context) {
	        return false; // never on success
	    }

	    @Override
    public boolean shouldTraverseFailure(Throwable failure, NodeContext context) {
        context.getLogger().info(
            "[FailureEdge] Routing failure from " + getSource().getName()
            + " -> " + getTarget().getName()
	        );
	        return true;
	    }

	    @Override
	    public String getLabel() {
	        return "failure";
	    }
}
