package tutorials.loops;

import java.util.concurrent.atomic.AtomicInteger;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;

/**
 * Loop tutorial 1:
 * Repeat the same node until the condition fails.
 */
final class RepeatSameNodeTutorial {

    private RepeatSameNodeTutorial() {}

    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();

        NodePlugin<Integer, Integer> node = new NodePlugin<>() {
            @Override
            public Integer process(Integer input, NodeContext context) {
                return counter.incrementAndGet();
            }
        };

        Graph graph = GraphBuilder.named("repeat-same-node")
            .addNode("loop", node)
            .addNode("end", (NodePlugin<Integer, Integer>) (i, ctx) -> i)
            .connect("loop", "end")
            .repeat("loop")
                .whileCondition((out, ctx) -> (int) out < 3)
                .max(5)
                .build()
            .build();

        new ParallelExecutor().run(graph, 1, new NodeContext());
        System.out.println("iterations=" + counter.get());
    }
}

