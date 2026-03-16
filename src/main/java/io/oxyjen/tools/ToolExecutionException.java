package io.oxyjen.tools;

import java.util.Collections;
import java.util.Map;

public class ToolExecutionException extends RuntimeException {
	
	private final String toolName;
	private final Map<String, Object> metadata;

	public ToolExecutionException(String toolName, String message) {
		super(message);
		this.toolName = toolName;
		this.metadata = Collections.emptyMap();
	}

	public ToolExecutionException(String toolName, String message, Map<String, Object> metadata) {
		super(message);
		this.toolName = toolName;
		this.metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
	}

	public ToolExecutionException(String toolName, String message, Throwable cause) {
		super(message, cause);
		this.toolName = toolName;
		this.metadata = Collections.emptyMap();
	}

	public ToolExecutionException(String toolName, String message, Map<String, Object> metadata, Throwable cause) {
	    super(message, cause);
	    this.toolName = toolName;
	    this.metadata = metadata != null ? Map.copyOf(metadata) : Collections.emptyMap();
	}

	public String getToolName() {
	    return toolName;
	}

	public Map<String, Object> getMetadata() {
	    return metadata;
	}

	@Override
	public String getMessage() {
	    return String.format("[%s] %s", toolName, super.getMessage());
	}
}
