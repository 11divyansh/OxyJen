package tutorials.graph;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.branching.BranchNode;
import io.oxyjen.graph.branching.MergeNode;
import io.oxyjen.graph.branching.RouterNode;

/**
 * Graph tutorial 1:
 * Branch, router, and merge.
 */
final class BranchRouterMergeTutorial {

    public record Ticket(String priority, String topic) {}

    private BranchRouterMergeTutorial() {}

    public static void main(String[] args) {
        Graph graph = GraphBuilder.named("branch-router-merge")
            .addNode("start", new StartNode())
            .addNode("branch", BranchNode.<Ticket>builder()
                .when("high", t -> "HIGH".equalsIgnoreCase(t.priority())).then("invoicePath")
                .orElse("genericPath")
                .build("branch"))
            .addNode("invoicePath", new PathNode("Invoice path"))
            .addNode("genericPath", new PathNode("Generic path"))
            .addNode("router", RouterNode.<String>builder()
                .route("summary", text -> true, "summaryNode")
                .route("risk", text -> true, "riskNode")
                .build("router"))
            .addNode("summaryNode", new TextNode("Summary analysis"))
            .addNode("riskNode", new TextNode("Risk analysis"))
            .addNode("merge", new MergeNode.Builder()
                .expect("summaryNode", "riskNode")
                .build("merge"))
            .connect("start", "branch")
            .connect("invoicePath", "router")
            .connect("genericPath", "router")
            .connect("summaryNode", "merge")
            .connect("riskNode", "merge")
            .build();

        System.out.println(graph.getName());
    }

    static final class StartNode implements NodePlugin<String, Ticket> {
        @Override
        public Ticket process(String input, NodeContext context) {
            return new Ticket("HIGH", input);
        }
    }

    static final class TextNode implements NodePlugin<Object, String> {
        private final String text;
        TextNode(String text) { this.text = text; }
        @Override
        public String process(Object input, NodeContext context) {
            return text;
        }
    }

    static final class PathNode implements NodePlugin<Ticket, String> {
        private final String label;
        PathNode(String label) { this.label = label; }
        @Override
        public String process(Ticket input, NodeContext context) {
            return label + ": " + input.topic();
        }
    }
}
