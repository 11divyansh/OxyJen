package io.oxyjen.execution.gather;

import java.util.ArrayList;
import java.util.List;

import io.oxyjen.execution.result.Success;
import io.oxyjen.execution.result.TaskResult;
import io.oxyjen.graph.concurrency.MapNode;
import io.oxyjen.graph.concurrency.ParallelNode;

public final class GatherCollectors {

    private GatherCollectors() {}
    public static List<?> collectFromParallelResult(
            ParallelNode.ParallelResult<?> result,
            CollectionMode mode
    ) {

        return switch (mode) {
            case SUCCESS_ONLY -> result.allResults()
                    .values()
                    .stream()
                    .filter(TaskResult::isSuccess)
                    .map(r -> ((Success<?>) r).value())
                    .toList();

            case FAILURES_ONLY -> result.allResults()
                    .values()
                    .stream()
                    .filter(TaskResult::isFailure)
                    .toList();

            case ALL_RESULTS -> new ArrayList<>(
                    result.allResults().values()
            );

            case COMPLETED_ONLY -> result.allResults()
                    .values()
                    .stream()
                    .filter(r ->
                            r.isSuccess() || r.isFailure()
                    )
                    .toList();
        };
    }
    public static List<?> collectFromMapResult(
            MapNode.MapResult<?> result,
            CollectionMode mode
    ) {
    	 return switch (mode) {
         case SUCCESS_ONLY -> result.toResultList()
                 .stream()
                 .filter(TaskResult::isSuccess)
                 .map(r -> ((Success<?>) r).value())
                 .toList();

         case FAILURES_ONLY -> result.toResultList()
                 .stream()
                 .filter(TaskResult::isFailure)
                 .toList();

         case ALL_RESULTS -> new ArrayList<>(
                 result.toResultList());

         case COMPLETED_ONLY -> result.toResultList()
                 .stream()
                 .filter(r -> r.isSuccess() || r.isFailure())
                 .toList();
     };
    }
    public static List<?> collectFromIterable(Iterable<?> iterable) {
        List<Object> list = new ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }
}