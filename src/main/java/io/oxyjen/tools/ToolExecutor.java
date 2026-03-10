package io.oxyjen.tools;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.JsonSerializer;
import io.oxyjen.llm.schema.SchemaValidator;
import io.oxyjen.tools.safety.ToolPermission;

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
    private final List<ToolPermission> permissions;
    
    /**
     * Create executor with default settings.
     * @param tools List of available tools
     */
    public ToolExecutor(Collection<Tool> tools) {
        this(tools, true, true, List.of());
    }
    public ToolExecutor(Collection<Tool> tools, ToolPermission... permissions) {
        this(tools, true, true, 
        		permissions == null ? List.of():List.of(permissions));
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
        boolean validateOutput,
        Collection<ToolPermission> permissions
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
        if (registry.isEmpty()) {
            throw new IllegalArgumentException("Cannot create executor with empty tool list");
        }
        this.inputValidator = new ToolValidator(strictInputValidation);
        this.validateOutput = validateOutput;    
        this.permissions = permissions == null
        		? List.of()
        		: permissions.stream().sorted(Comparator.comparingInt(ToolPermission::priority))
        		.collect(Collectors.toUnmodifiableList());
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
            for (ToolPermission permission : permissions) {
                if (!permission.isAllowed(tool, call, context)) {
                    String reason = permission.getReason(tool, call, context);
                    return buildFailure(
                        toolName,
                        reason != null ? reason : "Tool execution denied",
                        startTime,
                        context,
                        Map.of("_permissionDenied", true)
                    );
                }
            }
            long estimated = tool.estimateExecutionTime();
            if (estimated > 0 && estimated > 5000) {
            	context.getLogger().warning("Tool "+ toolName +" estimated execution time: "+ estimated+ "ms");
            }
            long timeout = tool.timeoutMs();
            if (timeout > 0) {
                context.getLogger().info(
                    "Tool " + toolName + " has timeout configured: " + timeout + "ms"
                );
                // Future enhancement: async execution with timeout enforcement
            }
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
            boolean success = false;
            try {
            	context.getLogger().info(
                        String.format("Executing tool: %s with args: %s", 
                            toolName, call.getArguments()));
            	for (ToolPermission permission : permissions) {
            	    permission.beforeExecution(tool, call, context);
            	}
                rawResult = tool.execute(call.getArguments(), context);
                success = !rawResult.isFailure();
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
            finally {
            	for (ToolPermission permission : permissions) {
                    permission.afterExecution(tool, call, success, context);
                }
            }
            if (rawResult.isFailure()) {   
                return buildFailure(
                		toolName,
                		rawResult.getError(),
                		startTime,
                		context,
                		rawResult.getMetadata());
            }
            Object typedOutput = rawResult.getOutput();
            Object jsonTree;
            try {
                jsonTree = (typedOutput instanceof Map ||
                		typedOutput instanceof Collection ||
                		typedOutput instanceof String ||
                		typedOutput instanceof Number ||
                		typedOutput instanceof Boolean)
                ? typedOutput
                : JsonSerializer.toJsonTree(typedOutput);;
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
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("_outputValidationErrors", outputValidation.errors());
                    if (call.getId() != null) metadata.put("_callId", call.getId());
                    return buildFailure(
                        toolName,
                        "Output validation failed: " + 
                            outputValidation.formatErrors(),
                        startTime,
                        context,
                        metadata
                    );
                }
            }
            long duration = System.currentTimeMillis() - startTime;
            context.getLogger().info(
                String.format("Tool %s completed successfully in %dms", 
                    toolName, duration)
            ); 
            Map <String, Object> metadata = new HashMap<>();
            if (rawResult.getMetadata() != null) metadata.putAll(rawResult.getMetadata());
            metadata.put("_jsonTree", jsonTree);
            if (call.getId() != null) metadata.put("_callId", call.getId());
            return ToolResult.builder()
                .toolName(toolName)
                .success(true)
                .output(typedOutput)
                .executionTimeMs(duration)
                .metadata(metadata)
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
        return buildFailure(toolName, error, startTime, context, null);
    }
    private ToolResult buildFailure(
            String toolName,
            String error,
            long startTime,
            NodeContext context,
            Map<String, Object> metadata
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
                .metadata(metadata != null ? metadata : Map.of())
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