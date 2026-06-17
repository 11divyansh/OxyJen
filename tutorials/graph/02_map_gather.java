package tutorials.graph;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.gather.CollectionMode;
import io.oxyjen.graph.concurrency.GatherNode;
import io.oxyjen.graph.concurrency.MapNode;

/**
 * Graph tutorial 2:
 * Batch fan-out and fan-in.
 */
final class MapGatherTutorial {

    private MapGatherTutorial() {}

    public static void main(String[] args) {
        MapNode<String, String> map = MapNode.<String, String>builder()
            .mapWith((doc, ctx) -> doc.toUpperCase())
            .maxInFlight(3)
            .timeout(60, TimeUnit.SECONDS)
            .continueOnError()
            .build("upper");

        GatherNode gather = GatherNode.builder()
            .collectMode(CollectionMode.SUCCESS_ONLY)
            .aggregate(GatherNode.Aggregation.LIST)
            .build("gather");

        var result = map.process(List.of("a", "b", "c"), new NodeContext());
        System.out.println(result.toSuccessfulList());
        System.out.println(gather.getClass().getSimpleName());
    }
}
