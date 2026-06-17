package tutorials.loops;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;

/**
 * Loop tutorial 3:
 * Bounded repair cycle using a loop and a validator node.
 */
final class RepairCycleTutorial {

    record Draft(String text, boolean valid) {}

    private RepairCycleTutorial() {}

    public static void main(String[] args) {
        NodePlugin<String, Draft> generate = (input, ctx) -> new Draft(input, false);
        NodePlugin<Draft, Draft> validate = (draft, ctx) -> draft.valid() ? draft : new Draft(draft.text() + " repaired", true);

        Graph graph = GraphBuilder.named("repair-cycle")
            .addNode("generate", generate)
            .addNode("validate", validate)
            .connect("generate", "validate")
            .repeat("validate")
                .whileCondition((out, ctx) -> out instanceof Draft draft && !draft.valid())
                .max(3)
                .build()
            .build();

        Draft result = new ParallelExecutor().runSingle(graph, "draft text", new NodeContext());
        System.out.println(result);
    }
}

