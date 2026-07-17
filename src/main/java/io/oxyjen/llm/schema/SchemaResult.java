package io.oxyjen.llm.schema;

import java.util.List;

import io.oxyjen.execution.metrics.NodeMetrics;

public final class SchemaResult {

    private final String rawJson;
    private final boolean valid;
    private final List<FieldError> errors;
    private final NodeMetrics.LlmNodeMetrics metrics;

    public SchemaResult(String rawJson, boolean valid, List<FieldError> errors, NodeMetrics.LlmNodeMetrics metrics) {
        this.rawJson = rawJson;
        this.valid = valid;
        this.errors = errors;
        this.metrics = metrics;
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
    
    public NodeMetrics.LlmNodeMetrics getMetrics(){
    	return metrics;
    }
}