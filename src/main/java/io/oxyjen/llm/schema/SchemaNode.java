package io.oxyjen.llm.schema;

import java.util.Map;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;
import io.oxyjen.util.JsonMapper;
import io.oxyjen.util.JsonParser;

/**
 * LLMNode with built-in JSON schema enforcement.
 * 
 * Combines LLMNode with SchemaEnforcer for structured outputs.
 */
public final class SchemaNode<T> implements NodePlugin<String, T> {
    
    private final ChatModel model;
    private final JSONSchema schema;
    private final int maxRetries;
    private final String memoryKey;
    private final Class<T> targetType;
    private final SchemaEnforcer enforcer;
    private final boolean failOnInvalid;
    
    private SchemaNode(Builder<T> builder) {
        this.model = builder.model;
        this.schema = builder.schema;
        this.maxRetries = builder.maxRetries;
        this.memoryKey = builder.memoryKey;
        this.targetType = builder.targetType;
        this.failOnInvalid = builder.failOnInvalid;
        this.enforcer = new SchemaEnforcer(model, schema, maxRetries, failOnInvalid);
    }
    
    @Override
    public T process(String input, NodeContext context) {
        if (memoryKey != null) {
            context.memory(memoryKey).append("user", input);
        } 
        SchemaResult result = enforcer.execute(input);
        if (memoryKey != null) {
            context.memory(memoryKey).append("assistant", result.getRawJson());
        }
        // Store schema result in context
        context.setMetadata("schemaResult", result);
        if (!result.isValid()) {
        	// store errors for graph-level decisions
        	context.setMetadata("schemaErrors", result.getErrors());
            if (failOnInvalid) {
                throw new IllegalStateException("Schema validation failed: " + result.getErrors());
            }
            return null; //soft-fail
        }
        try {
            if (targetType == Map.class) {
                return targetType.cast(JsonParser.parse(result.getRawJson()));
            } 
            return JsonMapper.deserialize(result.getRawJson(), targetType);
        } catch (Exception e) {
        	// store deserialization error
        	context.setMetadata("deserializationError", e.getMessage());
            if (failOnInvalid) {
                throw new RuntimeException("Deserialization failed", e);
            }
            return null; // soft-fail
        }
    }
    
    public static <T> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }
    
    public static class Builder<T> {
        private ChatModel model;
        private JSONSchema schema;
        private int maxRetries = 3;
        private String memoryKey;
        private Class<T> targetType;
        private boolean failOnInvalid = true;
        
        private Builder(Class<T> type) {
            this.targetType = type;
        }
        
        public Builder<T> model(ChatModel model) {
            this.model = model;
            return this;
        }
        
        public Builder<T> model(String modelName) {
            this.model = LLM.of(modelName);
            return this;
        }
        
        public Builder<T> schema(JSONSchema schema) {
            this.schema = schema;
            return this;
        }
        
        public Builder<T> maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder<T> memory(String memoryKey) {
            this.memoryKey = memoryKey;
            return this;
        }
        
        public Builder<T> failOnInvalid(boolean value) {
            this.failOnInvalid = value;
            return this;
        }
        
        public SchemaNode<T> build() {
            if (model == null) {
                throw new IllegalStateException("Model is required");
            }
            if (schema == null) {
                throw new IllegalStateException("Schema is required");
            }
            return new SchemaNode<>(this);
        }
    }
}