package io.oxyjen.tools.builtin;

import java.util.Map;
import java.util.Objects;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.JSONSchema.PropertySchema;
import io.oxyjen.tools.Tool;
import io.oxyjen.tools.ToolExecutionException;
import io.oxyjen.tools.ToolResult;
import io.oxyjen.tools.safety.ToolSandbox;

/**
 * File reading tool with built-in safety.
 * 
 * Real-world use cases:
 * - Read configuration files
 * - Process data files
 * - Load documents
 * - Access logs
 * 
 * Safety:
 * - Path validation via sandbox
 * - Size limits
 * - Encoding detection
 * - Read-only (never writes)
 * 
 * @version 0.4
 */
public final class FileReaderTool implements Tool {
    
    private final ToolSandbox sandbox;
    private final long maxFileSize;
    
    public FileReaderTool(ToolSandbox sandbox) {
        this(sandbox, 10_000_000); // 10MB default
    }
    
    public FileReaderTool(ToolSandbox sandbox, long maxFileSize) {
        this.sandbox = Objects.requireNonNull(sandbox, "Sandbox cannot be null");
        this.maxFileSize = maxFileSize;
    }
    
    @Override
    public String name() {
        return "file_read";
    }
    
    @Override
    public String description() {
        return """
            Read contents of a text file.
            
            Use for:
            - Reading configuration files
            - Loading data files
            - Accessing documents
            - Reading logs
            
            Returns file content as string.
            Max size: """ + (maxFileSize / 1_000_000) + "MB";
    }
    
    @Override
    public JSONSchema inputSchema() {
        return JSONSchema.object()
            .property("path",
                PropertySchema.string("File path to read")
                .build())
            .property("encoding",
                PropertySchema.string("Text encoding (default: UTF-8)")
                .build())
            .required("path")
            .build();
    }
    
    @Override
    public JSONSchema outputSchema() {
        return JSONSchema.object()
            .property("content",
                PropertySchema.string("File contents").build())
            .property("size",
                PropertySchema.number("File size in bytes").build())
            .property("lines",
                PropertySchema.number("Number of lines").build())
            .required("content", "size")
            .build();
    }

	@Override
	public ToolResult execute(Map<String, Object> input, NodeContext context) throws ToolExecutionException {
		// TODO Auto-generated method stub
		return null;
	}
}
