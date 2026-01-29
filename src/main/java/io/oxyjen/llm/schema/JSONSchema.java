package io.oxyjen.llm.schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a JSON schema for structured LLM outputs.
 * 
 * Supports basic types: string, number, boolean, object, array
 */
public final class JSONSchema {
    
    private final String type;
    private final Map<String, PropertySchema> properties;
    private final Set<String> required;
    private final String description;
    
    private JSONSchema(Builder builder) {
        this.type = builder.type;
        this.properties = new HashMap<>(builder.properties);
        this.required = new HashSet<>(builder.required);
        this.description = builder.description;
    }
    
    public static Builder object() {
        return new Builder("object");
    }
    
    public String toJSON() {
        // Simple JSON representation for OpenAI
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(type).append("\"");
        
        if (description != null) {
            sb.append(",\"description\":\"").append(description).append("\"");
        }
        
        if (!properties.isEmpty()) {
            sb.append(",\"properties\":{");
            boolean first = true;
            for (Map.Entry<String, PropertySchema> entry : properties.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(entry.getValue().toJSON());
                first = false;
            }
            sb.append("}");
        }
        
        if (!required.isEmpty()) {
            sb.append(",\"required\":[");
            boolean first = true;
            for (String req : required) {
                if (!first) sb.append(",");
                sb.append("\"").append(req).append("\"");
                first = false;
            }
            sb.append("]");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    public Map<String, PropertySchema> properties() {
        return new HashMap<>(properties);
    }
    
    public Set<String> required() {
        return new HashSet<>(required);
    }
    
    public static class Builder {
        private final String type;
        private final Map<String, PropertySchema> properties = new LinkedHashMap<>();
        private final Set<String> required = new HashSet<>();
        private String description;
        
        private Builder(String type) {
            this.type = type;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder property(String name, PropertySchema schema) {
            this.properties.put(name, schema);
            return this;
        }
        
        public Builder required(String... names) {
            this.required.addAll(Arrays.asList(names));
            return this;
        }
        
        public JSONSchema build() {
            return new JSONSchema(this);
        }
    }
    
    public static class PropertySchema {
        private final String type;
        private final String description;
        private final List<String> enumValues;
        
        private PropertySchema(String type, String description, List<String> enumValues) {
            this.type = type;
            this.description = description;
            this.enumValues = enumValues;
        }
        
        public static PropertySchema string(String description) {
            return new PropertySchema("string", description, null);
        }
        
        public static PropertySchema number(String description) {
            return new PropertySchema("number", description, null);
        }
        
        public static PropertySchema bool(String description) {
            return new PropertySchema("boolean", description, null);
        }
        
        public static PropertySchema enumOf(String description, String... values) {
            return new PropertySchema("string", description, Arrays.asList(values));
        }
        
        public String toJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(type).append("\"");
            
            if (description != null) {
                sb.append(",\"description\":\"").append(description).append("\"");
            }
            
            if (enumValues != null) {
                sb.append(",\"enum\":[");
                for (int i = 0; i < enumValues.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(enumValues.get(i)).append("\"");
                }
                sb.append("]");
            }
            
            sb.append("}");
            return sb.toString();
        }
    }
}