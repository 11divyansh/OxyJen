package io.oxyjen.graph.branching;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * A fan-in node that aggregates results from multiple parallel upstream branches.
 *
 * MergeNode is the counterpart to {@link RouterNode} — where RouterNode splits
 * execution into N parallel paths, MergeNode collects all their outputs and
 * combines them into a single value for downstream processing.
 *
 * MergeNode blocks until ALL expected upstream contributors have delivered their
 * result, or until a configurable timeout expires. Results are accumulated in a
 * thread-safe map keyed by contributor name.
 */
public class MergeNode implements NodePlugin<Object, Object> {

	public enum MergeStrategy {
        /** Returns the full {@code Map<String, Object>} of all contributions. */
        COLLECT_ALL,
        /** Returns the value from the first contribution to arrive (race). */
        FIRST_WINS,
        /** Returns a {@code List<Object>} of contribution values in arrival order. */
        LIST
    }
 
    // Context key prefix so multiple MergeNodes in one graph don't collide
    public static final String CONTEXT_KEY_PREFIX = "__merge__";
 
    private final String name;
    private final Set<String> expectedContributors;
    private final Function<Map<String, Object>, Object> mergeFunction;
    private final long timeoutMs;
 
    // Thread-safe accumulation of contributions from parallel branches
    private final Map<String, Object> contributions = new ConcurrentHashMap<>();
    private final CountDownLatch latch;
    private volatile Object firstArrival = null;  // for FIRST_WINS strategy
 
    private MergeNode(
            String name,
            Set<String> expectedContributors,
            Function<Map<String, Object>, Object> mergeFunction,
            long timeoutMs
    ) {
        this.name = Objects.requireNonNull(name);
        this.expectedContributors = Collections.unmodifiableSet(new LinkedHashSet<>(expectedContributors));
        this.mergeFunction = Objects.requireNonNull(mergeFunction);
        this.timeoutMs = timeoutMs;
        this.latch = new CountDownLatch(expectedContributors.size());
    }

	@Override
	public Object process(Object input, NodeContext context) {
		// TODO Auto-generated method stub
		return null;
	}
}
