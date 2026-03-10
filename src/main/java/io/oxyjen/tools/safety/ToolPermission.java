package io.oxyjen.tools.safety;

import io.oxyjen.core.NodeContext;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolCall;

public interface ToolPermission {
    // Check if a tool is allowed to execute.
    boolean isAllowed(Tool tool, ToolCall call, NodeContext context);
    
    // Get reason why a tool is denied
    default String getReason(Tool tool, ToolCall call, NodeContext context) {
        return isAllowed(tool, call, context) 
            ? null 
            : "Tool execution denied by permission policy";
    }
   
    default void beforeExecution(Tool tool, ToolCall call, NodeContext context) {}
    default void afterExecution(Tool tool, ToolCall call, boolean success, NodeContext context) {}
    /**
     * Optional policy priority(lower = earlier execution)
     */
    default int priority() {
        return 0;
    }
}