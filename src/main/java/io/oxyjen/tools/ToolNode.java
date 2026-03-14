package io.oxyjen.tools;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * Graph node that executes tools.
 * 
 * Architecture:
 * <pre>
 * Graph Executor
 *     ↓
 * ToolNode.process()
 *     ↓
 * ToolExecutor.execute()
 *     ↓
 * Tool.execute()
 * </pre>
 * 
 * Usage in Graph:
 * <pre>
 * ToolNode toolNode = new ToolNode(
 *     new CalculatorTool(),
 *     new WebSearchTool()
 * );
 * 
 * Graph workflow = GraphBuilder.named("agent-pipeline")
 *     .addNode(routerNode)
 *     .addNode(toolNode)
 *     .addNode(summaryNode)
 *     .build();
 * 
 * // Graph executor handles the flow
 * executor.run(workflow, input, context);
 * </pre>
 * 
 * Direct usage:
 * <pre>
 * ToolCall call = ToolCall.of("calculator", Map.of(
 *     "operation", "add",
 *     "a", 5,
 *     "b", 3
 * ));
 * 
 * ToolResult result = toolNode.process(call, context);
 * </pre>
 * 
 * @version 0.4
 */
public final class ToolNode implements NodePlugin<ToolCall, ToolResult> {

	private final ToolExecutor executor;
	private final String nodeName;
	private final String displayName;
	
	public ToolNode(Tool... tools) {
		this(Arrays.asList(tools));
	}
	public ToolNode(Collection<Tool> tools) {
		this("ToolNode", tools);
	}
	public ToolNode(String name, Tool... tools) {
		this(name, Arrays.asList(tools));
	}
	public ToolNode(String name, Collection<Tool> tools) {
		this.nodeName = Objects.requireNonNull(name, "Node name cannot be null");
		this.executor = ToolExecutor.of(
				Objects.requireNonNull(tools, "Tools cannot be null"));
		this.displayName = buildDisplayName();
	}
	/**
     * Create ToolNode with existing executor (for sharing executors).
     */
    public ToolNode(ToolExecutor executor) {
        this("ToolNode", executor);
    }
    public ToolNode(String name, ToolExecutor executor) {
        this.nodeName = name;
        this.executor = java.util.Objects.requireNonNull(executor, 
            "ToolExecutor cannot be null");
        this.displayName = buildDisplayName();
    }
    private String buildDisplayName() {
        return nodeName + "[" + executor.getToolCount() + " tools]";
    }
	
	@Override
	public ToolResult process(ToolCall input, NodeContext context) {
		Objects.requireNonNull(input, "ToolCall cannot be null");
        Objects.requireNonNull(context, "NodeContext cannot be null");
		return executor.execute(input, context);
	}
	@Override
    public String getName() {
        return displayName;
    }
    
    @Override
    public void onStart(NodeContext context) {
    	context.getLogger().log(
                java.util.logging.Level.INFO,
                () -> String.format(
                    "Starting %s with %d tools: %s",
                    displayName,
                    executor.getToolCount(),
                    executor.getToolNames()
                )
            );
    }
    
    @Override
    public void onFinish(NodeContext context) {
    	context.getLogger().log(
                java.util.logging.Level.INFO,
                () -> "Finished " + displayName
            );
    }
    
    @Override
    public void onError(Exception e, NodeContext context) {
        context.getLogger().log(java.util.logging.Level.SEVERE,
        		"Error in "+ displayName,
        		e
        );
    }
    public ToolExecutor getExecutor() {
        return executor;
    }
    public boolean hasTool(String toolName) {
        return executor.hasTool(toolName);
    }
    public Set<String> getToolNames() {
        return executor.getToolNames();
    }
}