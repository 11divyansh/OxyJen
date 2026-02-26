
package io.oxyjen.llm.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SchemaValidator {

    private final JSONSchema schema;

    public SchemaValidator(JSONSchema schema) {
        this.schema = schema;
    }
    public ValidationResult validate(String json) {
        List<FieldError> errors = new ArrayList<>();

        try {
            Object parsed = JsonParser.parse(json);
            if (!(parsed instanceof Map)) {
                errors.add(new FieldError(
                    "$",
                    FieldError.ErrorType.WRONG_TYPE,
                    "object",
                    parsed,
                    "Root JSON value must be an object"
                ));
                return new ValidationResult(false, errors);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) parsed;
            validateObject(root, schema, "$", errors);

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
    public ValidationResult validate(Object parsed) {
        List<FieldError> errors = new ArrayList<>();
        if (!(parsed instanceof Map)) {
            errors.add(new FieldError(
                "$",
                FieldError.ErrorType.WRONG_TYPE,
                "object",
                parsed,
                "Root must be an object"
            ));
            return new ValidationResult(false, errors);
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> root = (Map<String,Object>) parsed;
        validateObject(root, schema, "$", errors);
        return new ValidationResult(errors.isEmpty(), errors);
    }
    private static void validateObject(
            Map<String, Object> data,
            JSONSchema schema,
            String path,
            List<FieldError> errors
    ) {
    	for (String key : data.keySet()) {
    	    if (!schema.properties().isEmpty() &&
    	        !schema.properties().containsKey(key)) {
    	        errors.add(new FieldError(
    	            path + "." + key,
    	            FieldError.ErrorType.UNKNOWN_FIELD,
    	            "defined in schema",
    	            key,
    	            "Field not allowed by schema"
    	        ));
    	    }
    	}
        for (String required : schema.required()) {
            if (!data.containsKey(required)) {
                errors.add(new FieldError(
                    path + "." + required,
                    FieldError.ErrorType.MISSING_REQUIRED,
                    "present",
                    null,
                    "Missing required field"
                ));
            }
        }
        for (Map.Entry<String, JSONSchema.PropertySchema> entry : schema.properties().entrySet()) {
            String fieldName = entry.getKey();
            JSONSchema.PropertySchema prop = entry.getValue();
            String fieldPath = path + "." + fieldName;

            if (!data.containsKey(fieldName)) {
                continue;
            }

            Object value = data.get(fieldName);
            validateProperty(value, prop, fieldPath, errors);
        }
    }
    private static void validateProperty(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
    	if (value == null) {
    	    errors.add(new FieldError(
    	        path,
    	        FieldError.ErrorType.CONSTRAINT_VIOLATION,
    	        "non-null",
    	        null,
    	        "Null value not allowed"
    	    ));
    	    return;
    	}
        switch (prop.type()) {
            case STRING  -> validateString(value, prop, path, errors);
            case NUMBER  -> validateNumber(value, prop, path, errors);
            case BOOLEAN -> validateBoolean(value, prop, path, errors);
            case ARRAY   -> validateArray(value, prop, path, errors);
            case OBJECT  -> validateObjectProperty(value, prop, path, errors);
            default      -> errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                prop.type().toString().toLowerCase(),
                value,
                "Unknown schema type: " + prop.type()
            ));
        }
        if (prop.enumValues() != null && !prop.enumValues().isEmpty()) {
            String strVal = value.toString();
            if (!prop.enumValues().contains(strVal)) {
                errors.add(new FieldError(
                    path,
                    FieldError.ErrorType.INVALID_ENUM_VALUE,
                    prop.enumValues(),
                    value,
                    "Value not in allowed enum values"
                ));
            }
        }
    }
    private static void validateString(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
        if (!(value instanceof String str)) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                "string",
                value,
                "Expected string, got " + typeName(value)
            ));
            return;
        }
        if (prop.minLength() != null && str.length() < prop.minLength()) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.CONSTRAINT_VIOLATION,
                "minLength=" + prop.minLength(),
                str,
                "String length " + str.length() + " is less than minimum " + prop.minLength()
            ));
        }
        if (prop.maxLength() != null && str.length() > prop.maxLength()) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.CONSTRAINT_VIOLATION,
                "maxLength=" + prop.maxLength(),
                str,
                "String length " + str.length() + " exceeds maximum " + prop.maxLength()
            ));
        }
        if (prop.pattern() != null && !str.matches(prop.pattern())) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.CONSTRAINT_VIOLATION,
                "pattern=" + prop.pattern(),
                str,
                "String does not match required pattern"
            ));
        }
    }
    private static void validateNumber(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
        if (!(value instanceof Number number)) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                "number",
                value,
                "Expected number, got " + typeName(value)
            ));
            return;
        }
        double d = number.doubleValue();
        if (prop.minimum() != null && d < prop.minimum()) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.CONSTRAINT_VIOLATION,
                "minimum=" + prop.minimum(),
                value,
                "Value " + d + " is less than minimum " + prop.minimum()
            ));
        }
        if (prop.maximum() != null && d > prop.maximum()) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.CONSTRAINT_VIOLATION,
                "maximum=" + prop.maximum(),
                value,
                "Value " + d + " exceeds maximum " + prop.maximum()
            ));
        }
    }

    private static void validateBoolean(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
        if (!(value instanceof Boolean)) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                "boolean",
                value,
                "Expected boolean, got " + typeName(value)
            ));
        }
    }
    private static void validateArray(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
        if (!(value instanceof List<?> list)) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                "array",
                value,
                "Expected array, got " + typeName(value)
            ));
            return;
        }
        JSONSchema.PropertySchema itemSchema = prop.items();
        if (itemSchema == null) {
            return; 
        }
        for (int i = 0; i < list.size(); i++) {
            validateProperty(list.get(i), itemSchema, path + "[" + i + "]", errors);
        }
    }
    private static void validateObjectProperty(
            Object value,
            JSONSchema.PropertySchema prop,
            String path,
            List<FieldError> errors
    ) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            errors.add(new FieldError(
                path,
                FieldError.ErrorType.WRONG_TYPE,
                "object",
                value,
                "Expected object, got " + typeName(value)
            ));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        if (prop.nestedSchema() != null) {
            validateObject(map, prop.nestedSchema(), path, errors);
            return;
        }
        if (prop.additionalProperties() != null) {
            JSONSchema.PropertySchema valueSchema = prop.additionalProperties();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                validateProperty(entry.getValue(), valueSchema, path + "." + entry.getKey(), errors);
            }
        }
    }
    private static String typeName(Object value) {
        if (value == null)             return "null";
        if (value instanceof String)   return "string";
        if (value instanceof Boolean)  return "boolean";
        if (value instanceof Number)   return "number";
        if (value instanceof List)     return "array";
        if (value instanceof Map)      return "object";
        return value.getClass().getSimpleName();
    }
    public Map<String, Object> parseToMap(String json) {
        Object parsed = JsonParser.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("JSON is not an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
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
            return List.copyOf(errors);
        }
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        public List<FieldError> getErrorsForField(String fieldPath) {
            return errors.stream()
                .filter(e -> e.fieldPath().equals(fieldPath))
                .toList();
        }
        public String formatErrors() {
            return errors.stream()
                .map(FieldError::toString)
                .collect(Collectors.joining("\n"));
        }
    }
}