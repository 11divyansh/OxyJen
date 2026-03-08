package io.oxyjen.tools.safety;

import io.oxyjen.core.NodeContext;

public interface ToolPermission {
    // Check if a tool is allowed to execute.
    boolean isAllowed(String toolName, NodeContext context);
    
    // Get reason why a tool is denied
    default String getReason(String toolName, NodeContext context) {
        return isAllowed(toolName, context) 
            ? null 
            : "Tool execution denied by permission policy";
    }
   
     // Hook called before tool execution
    default void beforeExecution(String toolName, NodeContext context) {}
    
    // Hook called after tool execution
    default void afterExecution(String toolName, boolean success, NodeContext context) {}
}
