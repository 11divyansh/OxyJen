package io.oxyjen.tools;

import java.util.List;

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
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        private ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = List.copyOf(errors);
            this.warnings = List.copyOf(warnings);
        }
        
        public static ValidationResult valid(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }
        
        public static ValidationResult invalid(List<String> errors, List<String> warnings) {
            return new ValidationResult(false, errors, warnings);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public void throwIfInvalid() throws ToolValidationException {
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
                    ? "Validation: PASSED" 
                    : "Validation: PASSED with warnings: " + warnings;
            } else {
                return "Validation: FAILED - " + errors;
            }
        }
    }
}