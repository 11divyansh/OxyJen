package io.oxyjen.tools;

import java.util.Map;
import java.util.Objects;

import io.oxyjen.llm.schema.JsonParser;

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
 
    @SuppressWarnings("unused")
	private static Map<String, Object> parseArgumentsJson(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) {
            return Map.of();
        }      
        Object parsed = JsonParser.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException(
                "Tool arguments JSON must be an object, got: " +
                (parsed == null ? "null" : parsed.getClass().getSimpleName())
            );
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> map = (Map<String,Object>) parsed;
        return map;
    }
    
    public String getId() {
        return id;
    }

    public boolean hasId() {
        return id != null;
    }
    
    /**
     * Name of the tool to invoke.
     * Must match a registered Tool's name().
     */
    public String getName() {
        return name;
    }
    
    public Map<String, Object> getArguments() {
        return arguments;
    }
   
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String key, Class<T> type) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("Argument '%s' is %s, not %s",
                    key, value.getClass().getSimpleName(), type.getSimpleName())
            );
        }
        return (T) value;
    }
    
    public boolean hasArgument(String key) {
        return arguments.containsKey(key);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ToolCall{");
        if (id != null) {
            sb.append("id=").append(id).append(", ");
        }
        sb.append("name=").append(name);
        if (!arguments.isEmpty()) {
            sb.append(", args=").append(arguments);
        }
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolCall toolCall = (ToolCall) o;
        return Objects.equals(id, toolCall.id) &&
               Objects.equals(name, toolCall.name) &&
               Objects.equals(arguments, toolCall.arguments);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name, arguments);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private Map<String, Object> arguments;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder arguments(Map<String, Object> arguments) {
            this.arguments = arguments;
            return this;
        }
        
        public Builder argument(String key, Object value) {
            if (this.arguments == null) {
                this.arguments = new java.util.HashMap<>();
            }
            this.arguments.put(key, value);
            return this;
        }
        
        public ToolCall build() {
            return new ToolCall(id, name, arguments);
        }
    }
}