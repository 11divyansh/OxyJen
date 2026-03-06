package io.oxyjen.tools;

import java.util.Arrays;
import java.util.Collection;

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
		this.nodeName = name;
		this.executor = new ToolExecutor(tools);
	}
	
	@Override
	public ToolResult process(ToolCall input, NodeContext context) {
		// TODO Auto-generated method stub
		return executor.execute(input, context);
	}
	@Override
    public String getName() {
        return nodeName + "[" + executor.getToolCount() + " tools]";
    }
    
    @Override
    public void onStart(NodeContext context) {
        context.getLogger().info(
            String.format("Starting %s with %d available tools: %s",
                getName(),
                executor.getToolCount(),
                executor.getToolNames())
        );
    }
    
    @Override
    public void onFinish(NodeContext context) {
        context.getLogger().info("Finished " + getName());
    }
    
    @Override
    public void onError(Exception e, NodeContext context) {
        context.getLogger().severe(
            String.format("Error in %s: %s", getName(), e.getMessage())
        );
    }
}