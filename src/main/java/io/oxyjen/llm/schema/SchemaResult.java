package io.oxyjen.llm.schema;

import java.util.List;

public final class SchemaResult {

    private final String rawJson;
    private final boolean valid;
    private final List<FieldError> errors; 

    public SchemaResult(String rawJson, boolean valid, List<FieldError> errors) {
        this.rawJson = rawJson;
        this.valid = valid;
        this.errors = errors;
    }

    public boolean isValid() {
        return valid;
    }

    public String getRawJson() {
        return rawJson;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}