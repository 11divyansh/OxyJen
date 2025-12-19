package io.oxyjen.core.tests;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class DummyLifecycleNode implements NodePlugin<String,String> {

	 public volatile boolean started = false;
	    public volatile boolean finished = false;

	    @Override
	    public void onStart(NodeContext context) {
	        started = true;
	    }

	    @Override
	    public String process(String input, NodeContext context) {
	        // simple deterministic work
	        return input == null ? null : input.toUpperCase();
	    }

	    @Override
	    public void onFinish(NodeContext context) {
	        finished = true;
	    }

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return "DummyLifecycleNode";
		}
}
