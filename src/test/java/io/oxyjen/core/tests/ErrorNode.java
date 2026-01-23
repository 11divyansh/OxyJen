package io.oxyjen.core.tests;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

public class ErrorNode implements NodePlugin<String, String> {
    @Override
    public void onStart(NodeContext context) { /* optional */ }

    @Override
    public String process(String input, NodeContext context) {
        throw new RuntimeException("Intentional test exception from ErrorNode");
    }

    @Override
    public void onFinish(NodeContext context) { /* optional */ }
}

