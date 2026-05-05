package io.oxyjen.graph.branching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.graph.ParallelExecutor.NodeFailure;

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
    	final Map<String, Object> success = new ConcurrentHashMap<>();
    	final Map<String, Throwable> errors = new ConcurrentHashMap<>();
    	final List<String> arrivalOrder = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch latch;
        final AtomicInteger arrived = new AtomicInteger(0);
        MergeState(int expected) {
            this.latch = new CountDownLatch(expected);
        }
    }
    private final String name;
    private final Set<String> expectedContributors;
    private final Function<Map<String, Object>, Object> mergeFunction;
    private final long timeoutMs;
    private final MergeStrategy strategy;
 
    private MergeNode(
            String name,
            Set<String> expectedContributors,
            Function<Map<String, Object>, Object> mergeFunction,
            long timeoutMs,
            MergeStrategy strategy
    ) {
        this.name = Objects.requireNonNull(name);
        this.expectedContributors = Collections.unmodifiableSet(new LinkedHashSet<>(expectedContributors));
        this.mergeFunction = Objects.requireNonNull(mergeFunction);
        this.timeoutMs = timeoutMs;
        this.strategy = strategy;
    }
    
    // two separate key helpers
    private static String stateKey(String name) { return "__merge_state__" + name; }
    private static String nodeKey(String name)  { return "__merge_node__"  + name; }
    
    // Context State Management
    private MergeState getState(NodeContext context) {
        String key = stateKey(name);
        MergeState state = context.get(key);
        if (state == null) {
            throw new IllegalStateException(
                "MergeNode [" + name + "] not registered before execution"
            );
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
        context.getLogger().info(
        		"[DEBUG] Contribute called for: " + contributorName + " → " + name
        );
        boolean accepted;
        if (value instanceof NodeFailure failure) {
        	accepted = state.errors.putIfAbsent(contributorName, failure.error()) == null;
        	if (!accepted) {
        		context.getLogger().warning(
                        "[MergeNode:" + name + "] Duplicate failure from: " + contributorName
                );
                return;
        	}
        	int count = state.arrived.incrementAndGet();
        	context.getLogger().warning(
        			"[MergeNode:" + name + "] Received FAILURE from: " + contributorName +
                    " (" + count + "/" + expectedContributors.size() + " arrived)"
            );
        } else {
        	accepted = state.success.putIfAbsent(contributorName, value) == null;
        	if (!accepted) {
                context.getLogger().warning(
                    "[MergeNode:" + name + "] Duplicate contribution from: " + contributorName
                );
                return;
            }
        	state.arrivalOrder.add(contributorName);
        	int count = state.arrived.incrementAndGet();
            context.getLogger().info(
            		 "[MergeNode:" + name + "] Received SUCCESS from: " + contributorName +
                     " (" + count + "/" + expectedContributors.size() + " arrived)"
            );
        }
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
	             missing.removeAll(state.success.keySet());
	             missing.removeAll(state.errors.keySet());
	             throw new MergeTimeoutException(name, missing, timeoutMs);
	         }
	     } catch (InterruptedException e) {
	         Thread.currentThread().interrupt();
	         throw new RuntimeException("[MergeNode:" + name + "] interrupted while waiting for contributions", e);
	     }
	     Map<String, Object> successSnapshot = new LinkedHashMap<>(state.success);
	     Map<String, Throwable> errorSnapshot = new LinkedHashMap<>(state.errors);
	     context.getLogger().info("[MergeNode:" + name + "] All contributions received - merging." +
	    		 "success=" + state.success.size() +
	    	     ", errors=" + state.errors.size()
	     );
	     Object merged = null;
	     if (!successSnapshot.isEmpty()) {
	         try {
	        	 if (strategy == MergeStrategy.FIRST_WINS) {
	        		 String first = state.arrivalOrder.get(0);
	        		 merged = successSnapshot.get(first);
	        	 } else {
	        		 merged = mergeFunction.apply(successSnapshot);
	        	 }
	         } catch (Exception e) {
	             context.getLogger().severe(
	                 "[MergeNode:" + name + "] Merge function failed: " + e.getMessage()
	             );
	             throw e;
	         }
	     }
	     context.getLogger().info(
	    	"[MergeNode:" + name + "] Merged result = " + merged
	     );
	     return new MergeResult(state.success, state.errors, merged);
	}
	
	@Override
    public String getName() {
        return name;
    }
	
	/** Returns an immutable snapshot of contributions received so far. */
	public MergeResult getContributions(NodeContext context) {
		MergeState state = getState(context);
		Map<String, Object> successSnapshot = new LinkedHashMap<>(state.success);
	    Map<String, Throwable> errorSnapshot = new LinkedHashMap<>(state.errors);
		Object merged = null;
		if (!successSnapshot.isEmpty()) {
		    try {
		        merged = mergeFunction.apply(successSnapshot);
		    } catch (Exception e) {
		        context.getLogger().warning(
		            "[MergeNode:" + name + "] Merge function failed during getContributions(): " + e.getMessage()
		        );
		    }
		}
	    return new MergeResult(state.success, state.errors, merged);
	}
	 
	/** Returns the names of contributors still outstanding. */
	public Set<String> getMissingContributors(NodeContext context) {
		MergeState state = getState(context);
	    Set<String> missing = new LinkedHashSet<>(expectedContributors);
	    missing.removeAll(state.success.keySet());
	    missing.removeAll(state.errors.keySet());
	    return Collections.unmodifiableSet(missing);
	}
	
	public static final class Builder {

        private final Set<String> expectedContributors = new LinkedHashSet<>();
        private Function<Map<String, Object>, Object> mergeFunction; /** deprecated */
        private long timeoutMs = 30_000;
        private MergeStrategy strategy = MergeStrategy.COLLECT_ALL; // default

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
            this.strategy = strategy;
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
            Function<Map<String, Object>, Object> fn = switch (strategy) {
            	case COLLECT_ALL -> map -> new LinkedHashMap<>(map);
            	case LIST        -> map -> new ArrayList<>(map.values());
            	case FIRST_WINS  -> null; 
            };
            return new MergeNode(name, expectedContributors, mergeFunction, timeoutMs, strategy);
        }
    }
	
	public class MergeResult {

	    private final Map<String, Object> success;
	    private final Map<String, Throwable> errors;
	    private final Object merged;

	    public MergeResult(
	            Map<String, Object> success,
	            Map<String, Throwable> errors,
	            Object merged
	    ) {
	        this.success = Collections.unmodifiableMap(new LinkedHashMap<>(success));
	        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
	        this.merged = merged;
	    }

	    public Map<String, Object> getSuccess() {
	        return success;
	    }

	    public Map<String, Throwable> getErrors() {
	        return errors;
	    }

	    public boolean hasErrors() {
	        return !errors.isEmpty();
	    }
	    
	    public Object getMerged() {
	        return merged;
	    }

	    @SuppressWarnings("unchecked")
	    public <T> T get(String key) {
	        return (T) success.get(key);
	    }

	    @Override
	    public String toString() {
	        return "MergeResult{merged=" + merged+ ", success=" + success.keySet() +
	               ", errors=" + errors.keySet() + "}";
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