package io.oxyjen.tools;

public class ToolExecutionException extends RuntimeException {
	
private final String toolName;
    
    public ToolExecutionException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }
    
    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }
    
    public String getToolName() {
        return toolName;
    }
    
    @Override
    public String getMessage() {
        return String.format("[%s] %s", toolName, super.getMessage());
    }

}
