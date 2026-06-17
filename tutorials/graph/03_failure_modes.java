package tutorials.graph;

import java.util.concurrent.TimeUnit;

import io.oxyjen.core.NodeContext;
import io.oxyjen.execution.ExecutionRuntime;
import io.oxyjen.graph.concurrency.MapNode;

/**
 * Graph tutorial 3:
 * Fail-fast, collect-errors, and skip-failed behavior.
 */
final class FailureModesTutorial {

    private FailureModesTutorial() {}

    public static void main(String[] args) {
        ExecutionRuntime runtime = ExecutionRuntime.builder()
            .maxConcurrency(3)
            .failureMode(ExecutionRuntime.FailureMode.COLLECT_ERRORS)
            .defaultTimeout(60, TimeUnit.SECONDS)
            .build();

        MapNode<String, Integer> node = MapNode.<String, Integer>builder()
            .mapWith(s -> {
                if ("bad".equals(s)) throw new RuntimeException("boom");
                return s.length();
            })
            .continueOnError()
            .build("lengths");

        System.out.println(runtime.getFailureMode());
        System.out.println(node.process(java.util.List.of("a", "bad", "ccc"), new NodeContext()));
    }
}
