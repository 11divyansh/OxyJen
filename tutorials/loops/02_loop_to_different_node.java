package tutorials.loops;

import java.util.concurrent.atomic.AtomicInteger;

import io.oxyjen.core.Graph;
import io.oxyjen.core.GraphBuilder;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor;

/**
 * Loop tutorial 2:
 * Loop from one node back to a different earlier node.
 */
final class LoopToDifferentNodeTutorial {

    private LoopToDifferentNodeTutorial() {}

    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger();

        NodePlugin<Integer, Integer> a = (input, ctx) -> input + 1;
        NodePlugin<Integer, Integer> b = (input, ctx) -> counter.incrementAndGet();

        Graph graph = GraphBuilder.named("loop-different-node")
            .addNode("A", a)
            .addNode("B", b)
            .addNode("end", (NodePlugin<Integer, Integer>) (i, ctx) -> i)
            .connect("A", "B")
            .connect("B", "end")
            .loop("B")
                .to("A")
                .whileCondition((out, ctx) -> counter.get() < 3)
                .max(5)
                .build()
            .build();

        new ParallelExecutor().run(graph, 1, new NodeContext());
        System.out.println("iterations=" + counter.get());
    }
}

