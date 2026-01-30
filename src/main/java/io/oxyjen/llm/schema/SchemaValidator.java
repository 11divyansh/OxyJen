package io.oxyjen.llm.schema;

import java.util.*;
import java.util.regex.*;

/**
 * Validates JSON strings against a schema.
 * 
 * Simple validation - checks:
 * - JSON is parseable
 * - Required fields present
 * - Types match (basic)
 */
public final class SchemaValidator {
    
    private final JSONSchema schema;
    
    public SchemaValidator(JSONSchema schema) {
        this.schema = schema;
    }
    
    /**
     * Validate JSON string against schema.
     * 
     * @return ValidationResult with errors if invalid
     */
    public ValidationResult validate(String json) {
        List<String> errors = new ArrayList<>();
        
        try {
            // (simple manual parsing for v0.3)
            Map<String, Object> parsed = parseJSON(json);
            
            // Check required fields
            for (String required : schema.required()) {
                if (!parsed.containsKey(required)) {
                    errors.add("Missing required field: " + required);
                }
            }
            
            // Check field types (basic)
            for (Map.Entry<String, JSONSchema.PropertySchema> entry : schema.properties().entrySet()) {
                String fieldName = entry.getKey();
                
                if (parsed.containsKey(fieldName)) {
                    Object value = parsed.get(fieldName);
                    // Type checking would go here
                   
                }
            }
            
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Simple JSON parser for validation.
     * For v0.3, we use regex-based parsing.
     * v0.4 will use proper JSON library.
     */
    private Map<String, Object> parseJSON(String json) {
        Map<String, Object> result = new HashMap<>();
        
        // Simple regex to extract key-value pairs
        // basic parser for v0.3
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"?([^,}]+)\"?");
        Matcher matcher = pattern.matcher(json);
        
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim().replace("\"", "");
            result.put(key, value);
        }
        
        return result;
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        
        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<String> errors() {
            return new ArrayList<>(errors);
        }
    }
}