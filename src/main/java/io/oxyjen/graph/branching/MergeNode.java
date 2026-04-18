package io.oxyjen.graph.branching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 
    // Internal per-execution state
    private static class MergeState {
        final Map<String, Object> contributions = new ConcurrentHashMap<>();
        final CountDownLatch latch;
        MergeState(int expected) {
            this.latch = new CountDownLatch(expected);
        }
    }
    private final String name;
    private final Set<String> expectedContributors;
    private final Function<Map<String, Object>, Object> mergeFunction;
    private final long timeoutMs;
 
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
    }
    
    // two separate key helpers
    private static String stateKey(String name) { return "__merge_state__" + name; }
    private static String nodeKey(String name)  { return "__merge_node__"  + name; }
    
    // Context State Management
    private MergeState getState(NodeContext context) {
        String key = stateKey(name);
        MergeState state = context.get(key);
        if (state == null) {
            state = new MergeState(expectedContributors.size());
            context.set(key, state);
        }
        return state;
    }
    
    /**
     * Delivers a result from an upstream parallel branch.
     *
     * This must be called by each expected upstream node - typically in their
     * {@code onFinish()} hook - before MergeNode's {@code process()} can return.
     *
     * @param contributorName The name of the upstream node contributing (must match an expected contributor).
     * @param value           The output value from that upstream node.
     * @param context         The shared execution context (used for logging).
     */
    public void contribute(String contributorName, Object value, NodeContext context) {
    	MergeState state = getState(context);
        if (!expectedContributors.contains(contributorName)) {
            context.getLogger().warning(
                "[MergeNode:" + name + "] Unexpected contributor '" + contributorName
                    + "' - ignored. Expected: " + expectedContributors
            );
            return;
        }
 
        if (state.contributions.putIfAbsent(contributorName, value) != null) {
            context.getLogger().warning(
                "[MergeNode:" + name + "] Duplicate contribution from: " + contributorName
            );
            return;
        }
        context.getLogger().info(
            "[MergeNode:" + name + "] Received contribution from: " + contributorName
                + " (" + state.contributions.size() + "/" + expectedContributors.size() + " arrived)"
        );
        state.latch.countDown();
    }
 
    /**
     * Registers this MergeNode instance in the context so upstream parallel nodes can find it.
     * Called automatically by the executor before graph traversal begins.
     */
    public void register(NodeContext context) {
        context.set(nodeKey(name), this);                                    
        context.set(stateKey(name), new MergeState(expectedContributors.size()));
    }
    /**
     * Retrieve a MergeNode from context by name.
     * Used by upstream parallel nodes to look up the merge target in their onFinish().
     */
    @SuppressWarnings("unchecked")
    public static MergeNode fromContext(String mergeNodeName, NodeContext context) {
        MergeNode node = context.get(nodeKey(mergeNodeName));
        if (node == null) {
            throw new IllegalStateException(
                "MergeNode [" + mergeNodeName + "] not found in context. "
                    + "Ensure it is registered before the parallel branches execute."
            );
        }
        return node;
    }

	@Override
	public Object process(Object input, NodeContext context) {
		 MergeState state = getState(context);
	     context.getLogger().info(
	         "[MergeNode:" + name + "] Waiting for " + expectedContributors.size() + " contributions: " + expectedContributors
	     );
	     try {
	         boolean completed = state.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
	         if (!completed) {
	             Set<String> missing = new LinkedHashSet<>(expectedContributors);
	             missing.removeAll(state.contributions.keySet());
	             throw new MergeTimeoutException(name, missing, timeoutMs);
	         }
	     } catch (InterruptedException e) {
	         Thread.currentThread().interrupt();
	         throw new RuntimeException("[MergeNode:" + name + "] interrupted while waiting for contributions", e);
	     }
	     context.getLogger().info("[MergeNode:" + name + "] All contributions received - merging.");
	     return mergeFunction.apply(
	         Collections.unmodifiableMap(state.contributions)
	     );
	}
	
	@Override
    public String getName() {
        return name;
    }
	
	/** Returns an immutable snapshot of contributions received so far. */
	public Map<String, Object> getContributions(NodeContext context) {
		MergeState state = getState(context);
	    return Collections.unmodifiableMap(state.contributions);
	}
	 
	/** Returns the names of contributors still outstanding. */
	public Set<String> getMissingContributors(NodeContext context) {
		MergeState state = getState(context);
	    Set<String> missing = new LinkedHashSet<>(expectedContributors);
	    missing.removeAll(state.contributions.keySet());
	    return Collections.unmodifiableSet(missing);
	}
	
	public static final class Builder {

        private final Set<String> expectedContributors = new LinkedHashSet<>();
        private Function<Map<String, Object>, Object> mergeFunction;
        private long timeoutMs = 30_000;

        /**
         * Declare the names of upstream nodes whose output this MergeNode will collect.
         * These must exactly match {@link NodePlugin#getName()} of the contributing nodes.
         */
        public Builder expect(String... contributors) {
        	expectedContributors.addAll(Arrays.asList(contributors));
            return this;
        }
        public Builder expect(Collection<String> contributorNames) {
            expectedContributors.addAll(contributorNames);
            return this;
        }
        public Builder strategy(MergeStrategy strategy) {
            this.mergeFunction = switch (strategy) {
                case COLLECT_ALL -> map -> new LinkedHashMap<>(map);
                case FIRST_WINS  -> map -> map.values().iterator().next();
                case LIST        -> map -> new ArrayList<>(map.values());
            };
            return this;
        }
        public Builder mergeWith(Function<Map<String, Object>, Object> fn) {
            this.mergeFunction = Objects.requireNonNull(fn);
            return this;
        }
        public Builder timeout(long duration, TimeUnit unit) {
            this.timeoutMs = unit.toMillis(duration);
            return this;
        }
        public MergeNode build(String name) {
            if (expectedContributors.isEmpty()) {
                throw new IllegalStateException("MergeNode must expect at least one contributor");
            }
            if (mergeFunction == null) {
                mergeFunction = map -> new LinkedHashMap<>(map);
            }
            return new MergeNode(name, expectedContributors, mergeFunction, timeoutMs);
        }
    }
	
	public static class MergeTimeoutException extends RuntimeException {
        private final Set<String> missingContributors;
 
        public MergeTimeoutException(String nodeName, Set<String> missing, long timeoutMs) {
            super("MergeNode [" + nodeName + "] timed out after " + timeoutMs + "ms. "
                + "Still waiting for: " + missing);
            this.missingContributors = missing;
        }
 
        public Set<String> getMissingContributors() {
            return missingContributors;
        }
    }
}