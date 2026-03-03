package io.oxyjen.tools;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JsonSerializer;
import io.oxyjen.llm.schema.SchemaValidator;

/**
 * Runtime engine for executing tools with validation and safety checks.
 * 
 * This is the orchestration layer between ToolCall and Tool.
 * 
 * Responsibilities:
 * 1. Tool registry/lookup
 * 2. Input schema validation
 * 3. Safety checks (isSafe)
 * 4. Tool execution
 * 5. Output serialization
 * 6. Output schema validation
 * 7. Execution timing
 * 8. Error handling
 * 
 * Usage:
 * <pre>
 * ToolExecutor executor = new ToolExecutor(Arrays.asList(
 *     new CalculatorTool(),
 *     new WebSearchTool()
 * ));
 * 
 * ToolResult result = executor.execute(toolCall, context);
 * </pre>
 * 
 * Design principles:
 * - Never crashes (returns ToolResult.failure instead)
 * - Always validates before execution
 * - Measures execution time
 * - Logs everything
 * - Thread-safe (immutable registry)
 * 
 * @version 0.4
 */
public final class ToolExecutor {

	private final Map<String, Tool> registry;
    private final ToolValidator inputValidator;
    private final boolean validateOutput;
    
    /**
     * Create executor with default settings.
     * @param tools List of available tools
     */
    public ToolExecutor(Collection<Tool> tools) {
        this(tools, true, true);
    }
    
    /**
     * Create executor with custom settings.
     * 
     * @param tools List of available tools
     * @param strictInputValidation If true, fail on schema violations
     * @param validateOutput If true, validate tool output against outputSchema
     */
    public ToolExecutor(
        Collection<Tool> tools,
        boolean strictInputValidation,
        boolean validateOutput
    ) {
        Objects.requireNonNull(tools, "Tools collection cannot be null");
        this.registry = tools.stream()
            .collect(Collectors.toUnmodifiableMap(
                Tool::name,
                t -> t,
                (existing, duplicate) -> {
                    throw new IllegalArgumentException(
                        "Duplicate tool name: " + existing.name()
                    );
                }
            ));   
        this.inputValidator = new ToolValidator(strictInputValidation);
        this.validateOutput = validateOutput;     
        if (registry.isEmpty()) {
            throw new IllegalArgumentException("Cannot create executor with empty tool list");
        }
    }
    /**
     * Execute a tool call with full validation and safety checks.
     * 
     * main entry point
     * 
     * Flow:
     * 1. Look up tool by name
     * 2. Validate input against inputSchema
     * 3. Check isSafe()
     * 4. Execute tool
     * 5. Serialize output to JSON tree
     * 6. Validate output against outputSchema (if present)
     * 7. Return ToolResult with timing
     * 
     * @param call The tool call to execute
     * @param context Execution context for logging, state, etc.
     * @return ToolResult (never throws - failures return ToolResult.failure)
     */
    public ToolResult execute(ToolCall call, NodeContext context) {
        Objects.requireNonNull(call, "ToolCall cannot be null");
        Objects.requireNonNull(context, "NodeContext cannot be null"); 
        long startTime = System.currentTimeMillis();
        String toolName = call.getName();   
        try {
            Tool tool = registry.get(toolName);
            if (tool == null) {
                return buildFailure(
                    toolName,
                    "Unknown tool: '" + toolName + "'. Available tools: " + 
                        registry.keySet(),
                    startTime,
                    context
                );
            }    
            context.getLogger().info(
                String.format("Executing tool: %s with args: %s", 
                    toolName, call.getArguments())
            );
            ToolValidator.ValidationResult inputValidation = 
                inputValidator.validate(call, tool, context);
            if (!inputValidation.isValid()) {
                return buildFailure(
                    toolName,
                    "Input validation failed: " + 
                        String.join(", ", inputValidation.getErrors()),
                    startTime,
                    context
                );
            }
            if (inputValidation.hasWarnings()) {
                context.getLogger().warning(
                    "Tool validation warnings: " + 
                        String.join(", ", inputValidation.getWarnings())
                );
            }
            if (!tool.isSafe(call.getArguments(), context)) {
                return buildFailure(
                    toolName,
                    "Tool blocked by safety check",
                    startTime,
                    context
                );
            }
            ToolResult rawResult;
            try {
                rawResult = tool.execute(call.getArguments(), context);
            } catch (ToolExecutionException e) {
                return buildFailure(
                    toolName,
                    "Execution failed: " + e.getMessage(),
                    startTime,
                    context
                );
            } catch (Exception e) {
                context.getLogger().severe(
                    "Unexpected exception in tool " + toolName + ": " + 
                        e.getClass().getName() + " - " + e.getMessage()
                );
                return buildFailure(
                    toolName,
                    "Unexpected error: " + e.getClass().getSimpleName() + 
                        ": " + e.getMessage(),
                    startTime,
                    context
                );
            }
            if (rawResult.isFailure()) {
                long duration = System.currentTimeMillis() - startTime;
                context.getLogger().warning(
                    String.format("Tool %s failed: %s", toolName, rawResult.getError())
                );       
                return ToolResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .error(rawResult.getError())
                    .executionTimeMs(duration)
                    .metadata(rawResult.getMetadata())
                    .build();
            }
            Object output = rawResult.getOutput();
            Object jsonTree;
            try {
                jsonTree = JsonSerializer.toJsonTree(output);
            } catch (Exception e) {
                context.getLogger().severe(
                    "Failed to serialize tool output: " + e.getMessage()
                );
                return buildFailure(
                    toolName,
                    "Output serialization failed: " + e.getMessage(),
                    startTime,
                    context
                );
            }
            
            //output schema validation(optional)
            if (validateOutput && tool.outputSchema() != null) {
                SchemaValidator outputValidator = 
                    new SchemaValidator(tool.outputSchema());
                
                SchemaValidator.ValidationResult outputValidation = 
                    outputValidator.validate(jsonTree);
                
                if (!outputValidation.isValid()) {
                    context.getLogger().severe(
                        "Tool output does not match schema: " + 
                            outputValidation.formatErrors()
                    );
                    return buildFailure(
                        toolName,
                        "Output validation failed: " + 
                            outputValidation.formatErrors(),
                        startTime,
                        context
                    );
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            context.getLogger().info(
                String.format("Tool %s completed successfully in %dms", 
                    toolName, duration)
            );           
            return ToolResult.builder()
                .toolName(toolName)
                .success(true)
                .output(jsonTree)
                .executionTimeMs(duration)
                .metadata(rawResult.getMetadata())
                .build();           
        } catch (Exception e) {
            context.getLogger().severe(
                "Critical error in ToolExecutor: " + e.getMessage()
            );
            return buildFailure(
                toolName,
                "Executor failure: " + e.getClass().getSimpleName() + 
                    ": " + e.getMessage(),
                startTime,
                context
            );
        }
    }
    private ToolResult buildFailure(
            String toolName,
            String error,
            long startTime,
            NodeContext context
        ) {
            long duration = System.currentTimeMillis() - startTime;
            context.getLogger().severe(
                String.format("Tool %s failed: %s", toolName, error)
            );       
            return ToolResult.builder()
                .toolName(toolName)
                .success(false)
                .error(error)
                .executionTimeMs(duration)
                .build();
        }
        public Tool getTool(String name) {
            return registry.get(name);
        }
        public boolean hasTool(String name) {
            return registry.containsKey(name);
        }
        public Set<String> getToolNames() {
            return registry.keySet();
        }
        public Collection<Tool> getTools() {
            return registry.values();
        }
        public int getToolCount() {
            return registry.size();
        }
      
        @Override
        public String toString() {
            return String.format(
                "ToolExecutor{tools=%d, validateOutput=%s}",
                registry.size(), validateOutput
            );
        }
}