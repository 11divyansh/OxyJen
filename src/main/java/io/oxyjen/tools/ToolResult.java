package io.oxyjen.tools;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Result of a tool execution.
 * 
 * Immutable value object containing:
 * - Success/failure status
 * - Output data (if successful)
 * - Error information (if failed)
 * - Metadata (timing, cost, etc.)
 * 
 * @version 0.4
 */
public final class ToolResult {
	private final boolean success;
    private final Object output;
    private final String error;
    private final String toolName;
    private final Instant timestamp;
    private final long executionTimeMs;
    private final Map<String, Object> metadata;
    
    private ToolResult(Builder builder) {
        this.success = builder.success;
        this.output = builder.output;
        this.error = builder.error;
        this.toolName = builder.toolName;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.executionTimeMs = builder.executionTimeMs;
        this.metadata = builder.metadata != null 
            ? Map.copyOf(builder.metadata) 
            : Map.of();
    }
    
    public static ToolResult success(String toolName, Object output) {
        return new Builder()
            .toolName(toolName)
            .success(true)
            .output(output)
            .build();
    }
    
    public static ToolResult failure(String toolName, String error) {
        return new Builder()
            .toolName(toolName)
            .success(false)
            .error(error)
            .build();
    }
    
    public static ToolResult failure(String toolName, Exception e) {
        return new Builder()
            .toolName(toolName)
            .success(false)
            .error(e.getClass().getSimpleName() + ": " + e.getMessage())
            .build();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isFailure() {
        return !success;
    }
    
    public Object getOutput() {
        if (!success) {
            throw new IllegalStateException(
                "Cannot get output from failed result. Error: " + error
            );
        }
        return output;
    }
    
    /**
     * @throws ClassCastException if output is not of expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutputAs(Class<T> type) {
        return (T) getOutput();
    }

    public Optional<Object> getOutputSafe() {
        return success ? Optional.ofNullable(output) : Optional.empty();
    }
    
    public String getError() {
        if (success) {
            throw new IllegalStateException("Cannot get error from successful result");
        }
        return error;
    }
    
    public Optional<String> getErrorSafe() {
        return success ? Optional.empty() : Optional.ofNullable(error);
    }
    
    public String getToolName() {
        return toolName;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }
    
    public String toObservation() {
        if (success) {
            return String.format("[%s] SUCCESS: %s", toolName, formatOutput());
        } else {
            return String.format("[%s] ERROR: %s", toolName, error);
        }
    }
    
    private String formatOutput() {
        if (output == null) {
            return "null";
        }
        
        String str = output.toString();
        int maxLen = 500;
        if (str.length() > maxLen) {
            return str.substring(0, maxLen) + "... (truncated)";
        } 
        return str;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ToolResult{tool=%s, success=%s, time=%dms}",
            toolName, success, executionTimeMs
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private boolean success;
        private Object output;
        private String error;
        private String toolName;
        private Instant timestamp;
        private long executionTimeMs;
        private Map<String, Object> metadata;
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder output(Object output) {
            this.output = output;
            return this;
        }
        
        public Builder error(String error) {
            this.error = error;
            return this;
        }
        
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder executionTimeMs(long ms) {
            this.executionTimeMs = ms;
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public ToolResult build() {
            if (toolName == null) {
                throw new IllegalStateException("toolName is required");
            }
            return new ToolResult(this);
        }
    }
}