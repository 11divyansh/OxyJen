package io.oxyjen.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.oxyjen.core.NodeContext;
import io.oxyjen.llm.schema.FieldError;
import io.oxyjen.llm.schema.JSONSchema;
import io.oxyjen.llm.schema.SchemaValidator;

/**
 * Validates tool calls against tool schemas.
 * 
 * This is a critical security layer. Never execute unvalidated tool calls.
 * 
 * @version 0.4
 */
public final class ToolValidator {
    
    private final boolean strictMode;
    
    /**
     * by default strict mode on.
     */
    public ToolValidator() {
        this(true);
    }
    
    /**
     * Create validator with custom settings.
     * 
     * @param strictMode If true, fail on any validation error.
     *                   If false, allow some non-critical issues (use with caution).
     */
    public ToolValidator(boolean strictMode) {
        this.strictMode = strictMode;
    }
    /**
     * Validate a tool call before execution.
     * 
     * Checks:
     * 1. Tool exists
     * 2. Arguments match schema
     * 3. Required fields present
     * 4. Types correct
     * 5. Safety checks pass
     * 
     * @param call The tool call to validate
     * @param tool The tool being called
     * @param context Execution context (for safety checks)
     * @return Validation result
     */
    public ValidationResult validate(
            ToolCall call,
            Tool tool,
            NodeContext context
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (call == null) {
            errors.add("ToolCall cannot be null");
            return ValidationResult.invalid(errors, warnings);
        }

        if (tool == null) {
            errors.add("Tool cannot be null");
            return ValidationResult.invalid(errors, warnings);
        }
        if (!call.getName().equals(tool.name())) {
            errors.add(String.format(
                    "Tool name mismatch: call='%s', tool='%s'",
                    call.getName(), tool.name()
            ));
        }
        JSONSchema schema = tool.inputSchema();
        if (schema != null) {
            try {
                SchemaValidator.ValidationResult result =
                        new SchemaValidator(schema).validate(call.getArguments());

                if (!result.isValid()) {
                    errors.addAll(
                        result.errors().stream()
                            .map(FieldError::toString)
                            .toList()
                    );
                }

            } catch (Exception e) {
                errors.add("Schema validation failed: " + e.getMessage());
            }

        } else {
            warnings.add("Tool has no input schema");
        }
        try {
            if (!tool.isSafe(call.getArguments(), context)) {
                errors.add("Tool safety check failed for: " + tool.name());
            }
        } catch (Exception e) {
            errors.add("Tool safety check threw exception: " + e.getMessage());
        }

        //Strict mode: detect unknown fields manually if needed
        if (strictMode && schema != null) {
            Set<String> allowed = schema.properties().keySet();

            for (String key : call.getArguments().keySet()) {
                if (!allowed.contains(key)) {
                    errors.add("Unexpected argument: " + key);
                }
            }
        }
        return errors.isEmpty()
                ? ValidationResult.valid(warnings)
                : ValidationResult.invalid(errors, warnings);
    }
    // implement tool.outputSchema() to check for output schema
   
    public static final class ValidationResult {

        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        private ValidationResult(boolean valid,
                                 List<String> errors,
                                 List<String> warnings) {
            this.valid = valid;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }

        public static ValidationResult valid(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }

        public static ValidationResult invalid(List<String> errors,
                                               List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public void throwIfInvalid() {
            if (!valid) {
                throw new ToolValidationException(
                        "Tool validation failed: " + String.join(", ", errors)
                );
            }
        }
        @Override
        public String toString() {
            if (valid) {
                return warnings.isEmpty()
                        ? "Validation PASSED"
                        : "Validation PASSED with warnings: " + warnings;
            }
            return "Validation FAILED: " + errors;
        }
    }
}