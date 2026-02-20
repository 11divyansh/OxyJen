package io.oxyjen.tools;

import java.util.Map;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;

/**
 * A tool that can be executed by agents or in graphs.
 * 
 * Tools are DECOUPLED from LLMs - they can be called:
 * - By an LLM via AgentNode
 * - Directly in a graph via ToolNode
 * - Programmatically by user code
 * 
 * Design philosophy:
 * - Tools declare their schema (self-describing)
 * - Tools are stateless (all state in NodeContext)
 * - Tools validate inputs before execution
 * - Tools handle their own errors gracefully
 * 
 * @version 0.4
 */
public interface Tool {
    
    /**
     * Unique identifier for this tool.
     * Used by LLMs to reference the tool.
     * 
     * Convention: lowercase_with_underscores
     * Examples: "web_search", "read_file", "send_email"
     */
    String name();
    
    /**
     * Human-readable description of what this tool does.
     * 
     * This is shown to the LLM to help it decide when to use the tool.
     * Be specific and include:
     * - What the tool does
     * - When to use it
     * - Any important limitations
     * 
     */
    String description();
    
    /**
     * JSON Schema defining the expected input parameters.
     * 
     * The LLM uses this to generate correctly-formatted tool calls.
     * ToolValidator uses this to validate inputs before execution.
     * 
     */
    JSONSchema inputSchema();
    
    /**
     * Execute the tool with validated inputs.
     * 
     * @param input Validated input parameters (matches inputSchema)
     * @param context Shared execution context for logging, memory, etc.
     * @return Result of tool execution
     * @throws ToolExecutionException if execution fails
     */
    ToolResult execute(Map<String, Object> input, NodeContext context) 
        throws ToolExecutionException;
    
    // For typed objects
    default <T> ToolResult execute(T input, NodeContext context) {
        throw new UnsupportedOperationException(
            "Typed execution not implemented for " + name()
        );
    }
    
    default boolean deterministic() { return true; }
    
    /**
     * Optional: Check if this tool is safe to execute with given inputs.
     * 
     * Override this for tools that need runtime safety checks beyond schema validation.
     * 
     * @param input The validated input parameters
     * @param context Execution context
     * @return true if safe to execute, false otherwise
     */
    default boolean isSafe(Map<String, Object> input, NodeContext context) {
        return true; // Default
    }
    
    /**
     * Optional: Estimate execution cost/time.
     * 
     * Useful for:
     * - Agent planning (choose cheaper tools when possible)
     * - Timeouts (set appropriate timeout based on estimate)
     * - User warnings (alert if tool is expensive)
     * 
     * @return Estimated execution time in milliseconds, or -1 if unknown
     */
    default long estimateExecutionTime() {
        return -1; 
    }
}