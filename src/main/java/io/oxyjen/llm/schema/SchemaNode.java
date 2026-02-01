package io.oxyjen.llm.schema;

import java.util.Map;

import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.LLM;

/**
 * LLMNode with built-in JSON schema enforcement.
 * 
 * Combines LLMNode with SchemaEnforcer for structured outputs.
 */
public final class SchemaNode implements NodePlugin<String, Map<String,Object>> {
    
    private final ChatModel model;
    private final JSONSchema schema;
    private final int maxRetries;
    private final String memoryKey;
    
    private SchemaNode(Builder builder) {
        this.model = builder.model;
        this.schema = builder.schema;
        this.maxRetries = builder.maxRetries;
        this.memoryKey = builder.memoryKey;
    }
    
    @Override
    public Map<String, Object> process(String input, NodeContext context) {
        // Store user input in memory
        if (memoryKey != null) {
            context.memory(memoryKey).append("user", input);
        }
        
        // Execute with schema enforcement
        SchemaEnforcer enforcer = new SchemaEnforcer(model, schema, maxRetries);
        String jsonOutput = enforcer.execute(input);
        
        // Store output in memory
        if (memoryKey != null) {
            context.memory(memoryKey).append("assistant", jsonOutput);
        }
        
        return new SchemaValidator(schema).parseToMap(jsonOutput);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ChatModel model;
        private JSONSchema schema;
        private int maxRetries = 3;
        private String memoryKey;
        
        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }
        
        public Builder model(String modelName) {
            this.model = LLM.of(modelName);
            return this;
        }
        
        public Builder schema(JSONSchema schema) {
            this.schema = schema;
            return this;
        }
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder memory(String memoryKey) {
            this.memoryKey = memoryKey;
            return this;
        }
        
        public SchemaNode build() {
            if (model == null) {
                throw new IllegalStateException("Model is required");
            }
            if (schema == null) {
                throw new IllegalStateException("Schema is required");
            }
            return new SchemaNode(this);
        }
    }
}