package io.oxyjen.llm.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        List<FieldError> errors = new ArrayList<>();
        
        try {
            // (simple manual parsing for v0.3)
            Map<String, Object> parsed = parseJSON(json);
            
            // Check required fields
            for (String required : schema.required()) {
                if (!parsed.containsKey(required)) {
                    errors.add(new FieldError(
                    	"$."+required,
                    	FieldError.ErrorType.MISSING_REQUIRED,
                    	"present",
                    	null,
                    	"Missing required field"
                    ));
                }
            }
            
            // Check field types (basic)
            for (Map.Entry<String, JSONSchema.PropertySchema> entry : schema.properties().entrySet()) {
                String fieldName = entry.getKey();
                JSONSchema.PropertySchema prop = entry.getValue();
                if(!parsed.containsKey(fieldName)) {
                	continue;
                }
                Object value = parsed.get(fieldName);
                switch (prop.type()) {
                	
                	case STRING -> {
                		if (!(value instanceof String)) {
                			errors.add(new FieldError(
                				"$." + fieldName,
                				FieldError.ErrorType.WRONG_TYPE,
                				"string",
                				value,
                				"Expected string"
                			));
                			continue;
                		}
                	}

                	case NUMBER -> {
                		try {
                			Double.parseDouble(value.toString());
                		} catch (Exception e) {
                			errors.add(new FieldError(
                				"$." + fieldName,
                				FieldError.ErrorType.WRONG_TYPE,
                				"number",
                				value,
                				"Expected number"
                			));
                			continue;
                		}
                	}
                	
                	case BOOLEAN -> {
                		String v = value.toString().toLowerCase();
                		if (!v.equals("true") && !v.equals("false")) {
                			errors.add(new FieldError(
                				"$." + fieldName,
                				FieldError.ErrorType.WRONG_TYPE,
                				"boolean",
                				value,
                				"Expected boolean"
                			));
                			continue;
                		}
                	}
                	default -> {}
                }
            }
            
        } catch (Exception e) {
            errors.add(new FieldError(
            		"$",
            		FieldError.ErrorType.PARSE_ERROR,
            		"valid JSON",
            		json,
            		e.getMessage()
            	));
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
        private final List<FieldError> errors;
        
        public ValidationResult(boolean valid, List<FieldError> errors) {
            this.valid = valid;
            this.errors = errors;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public List<FieldError> errors() {
            return new ArrayList<>(errors);
        }
    }
}