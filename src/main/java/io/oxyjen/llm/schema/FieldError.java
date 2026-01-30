package io.oxyjen.llm.schema;

public class FieldError {
    
    public enum ErrorType {
        MISSING_REQUIRED,
        WRONG_TYPE,
        INVALID_ENUM_VALUE,
        INVALID_FORMAT,
        OUT_OF_RANGE,
        PARSE_ERROR
    }
    
    private final String fieldPath;
    private final ErrorType errorType;
    private final Object expected;
    private final Object received;
    private final String message;
    
    public FieldError(
        String fieldPath,
        ErrorType errorType,
        Object expected,
        Object received,
        String message
    ) {
        this.fieldPath = fieldPath;
        this.errorType = errorType;
        this.expected = expected;
        this.received = received;
        this.message = message;
    }
    
    public String fieldPath() { return fieldPath; }
    public ErrorType errorType() { return errorType; }
    public Object expected() { return expected; }
    public Object received() { return received; }
    public String message() { return message; }
    
    @Override
    public String toString() {
        return String.format(
            "Field '%s': %s (expected %s, got %s)",
            fieldPath, message, expected, received
        );
    }
}