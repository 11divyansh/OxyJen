package io.oxyjen.tools;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an LLM's request to call a tool.
 * 
 * When an LLM decides to use a tool, it generates a ToolCall containing:
 * - Which tool to invoke (name)
 * - What arguments to pass (arguments)
 * - Optional call ID for tracking (in multi-tool scenarios)
 * 
 * This is an immutable value object.
 * 
 * @version 0.4
 */
public final class ToolCall {
	private final String id;           
    private final String name;        
    private final Map<String, Object> arguments;
    
    private ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "Tool name cannot be null");
        this.arguments = arguments != null 
            ? Map.copyOf(arguments) 
            : Map.of();
    }
    
    public static ToolCall of(String name, Map<String, Object> arguments) {
        return new ToolCall(null, name, arguments);
    }

    public static ToolCall of(String id, String name, Map<String, Object> arguments) {
        return new ToolCall(id, name, arguments);
    }
}