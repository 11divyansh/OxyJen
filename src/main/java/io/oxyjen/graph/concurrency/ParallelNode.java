package io.oxyjen.graph.concurrency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class ParallelNode<I,O> implements NodePlugin<I, ParallelNode.ParallelResult<O>> {

	 public enum FailureStrategy {
	        FAIL_FAST,
	        COLLECT_ERRORS
	    }

	    public static final class ParallelResult<O> {
	        private final Map<String, O> outputs;
	        private final Map<String, Throwable> errors;
	        private final List<String> completionOrder;

	        ParallelResult(
	                Map<String, O> outputs,
	                Map<String, Throwable> errors,
	                List<String> completionOrder
	        ) {
	            this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
	            this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
	            this.completionOrder = Collections.unmodifiableList(new ArrayList<>(completionOrder));
	        }

	        public O get(String name) { return outputs.get(name); }
	        public Map<String, O> allOutputs() { return outputs; }
	        public Map<String, Throwable> allErrors() { return errors; }
	        public boolean hasErrors() { return !errors.isEmpty(); }
	        public List<String> completionOrder() { return completionOrder; }
	    }

		@Override
		public ParallelResult process(I input, NodeContext context) {
			// TODO Auto-generated method stub
			return null;
		}
}