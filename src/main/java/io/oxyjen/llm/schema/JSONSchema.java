package io.oxyjen.llm.schema;

import java.util.Arrays;
import java.util.Collection;
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
    
    private final SchemaType type;
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
        return new Builder(SchemaType.OBJECT);
    }
    
    public String toJSON() {
        // Simple JSON representation for OpenAI
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(type.json()).append("\"");
        
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
        private final SchemaType type;
        private final Map<String, PropertySchema> properties = new LinkedHashMap<>();
        private final Set<String> required = new HashSet<>();
        private String description;
        
        private Builder(SchemaType type) {
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
        
        public Builder property(String name, PropertySchema.Builder schemaBuilder) {
            this.properties.put(name, schemaBuilder.build());
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
        private final SchemaType type;
        private final String description;
        private final List<String> enumValues;
        
        private final JSONSchema nestedSchema;  // for nested objects
        private final String pattern;           // regex for strings
        private final Integer minLength;        // min string length
        private final Integer maxLength;        // max string length
        private final Long minimum;             // min number value
        private final Long maximum;             // Max number value
        private final PropertySchema items;		// for arrays

        
        public static class Builder {
            private SchemaType type;
            private String description;
            private List<String> enumValues;
            private JSONSchema nestedSchema;
            private String pattern;
            private Integer minLength;
            private Integer maxLength;
            private Long minimum;
            private Long maximum;
            private PropertySchema items;
            
            public Builder type(SchemaType type) {
                this.type = type;
                return this;
            }
            
            public Builder description(String description) {
                this.description = description;
                return this;
            }
            
            public Builder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }
            
            public Builder minLength(int minLength) {
                this.minLength = minLength;
                return this;
            }
            
            public Builder maxLength(int maxLength) {
                this.maxLength = maxLength;
                return this;
            }
            
            public Builder minimum(long minimum) {
                this.minimum = minimum;
                return this;
            }
            
            public Builder maximum(long maximum) {
                this.maximum = maximum;
                return this;
            }
            
            public Builder nestedSchema(JSONSchema schema) {
                this.nestedSchema = schema;
                return this;
            }
            
            public Builder enumValues(List<String> values) {
                this.enumValues = values;
                return this;
            }
            
            public Builder items(PropertySchema itemSchema) {
                this.items = itemSchema;
                return this;
            }
            
            public PropertySchema build() {
            	if (type == null) {
                    throw new IllegalStateException("Schema type is required");
                }
                if (type == SchemaType.OBJECT && nestedSchema == null) {
                    throw new IllegalStateException(
                        "Object schema must have nestedSchema"
                    );
                }
                if (type == SchemaType.OBJECT && nestedSchema.properties().isEmpty()) {
                    throw new IllegalStateException("Object schema must have properties");
                }
                if (type == SchemaType.ARRAY && items == null) {
                    throw new IllegalStateException("Array schema must define items");
                }

                if (type != SchemaType.ARRAY && items != null) {
                    throw new IllegalStateException("Only array schemas can define items");
                }
                return new PropertySchema(this);
            }
        }
        
        public static Builder string(String description) {
            return new Builder()
                .type(SchemaType.STRING)
                .description(description);
        }
        
        public static Builder number(String description) {
            return new Builder()
                .type(SchemaType.NUMBER)
                .description(description);
        }
        
        public static Builder bool(String description) {
            return new Builder()
                .type(SchemaType.BOOLEAN)
                .description(description);
        }
        
        public static Builder enumOf(String description, String... values) {
            return new Builder()
                .type(SchemaType.STRING)
                .description(description)
                .enumValues(Arrays.asList(values));
        }
        
        public static Builder array(String description) { // may deprecate(only for prototype)
            return new Builder()
                .type(SchemaType.ARRAY)
                .description(description);
        }
        public static Builder array(PropertySchema items) {
            return new Builder()
                .type(SchemaType.ARRAY)
                .items(items);
        }
        public static Builder array(String description, PropertySchema items) {
            return new Builder()
                .type(SchemaType.ARRAY)
                .description(description)
                .items(items);
        }
        public static Builder array(PropertySchema.Builder itemsBuilder) { // build PropertySchema without .build()
            return new Builder()
                .type(SchemaType.ARRAY)
                .items(itemsBuilder.build());
        }
        
        public static PropertySchema object(String description, JSONSchema nestedSchema) {
            return new Builder()
                .type(SchemaType.OBJECT)
                .description(description)
                .nestedSchema(nestedSchema)
                .build();
        }
        
        private PropertySchema(Builder b) {
            this.type = b.type;
            this.description = b.description;
            this.enumValues = b.enumValues != null
            	    ? List.copyOf(b.enumValues)
            	    : null;
            this.nestedSchema = b.nestedSchema;
            this.pattern = b.pattern;
            this.minLength = b.minLength;
            this.maxLength = b.maxLength;
            this.minimum = b.minimum;
            this.maximum = b.maximum;
            this.items = b.items;
        }
        
        public SchemaType type() {
        	return type;
        }
        
        public List<String> enumValues(){
        	return enumValues;
        }
        
        public String toJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"").append(type.json()).append("\"");
            
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
            
            if (pattern != null) {
                sb.append(",\"pattern\":\"").append(pattern).append("\"");
            }
            
            if (minLength != null) {
                sb.append(",\"minLength\":").append(minLength);
            }
            
            if (maxLength != null) {
                sb.append(",\"maxLength\":").append(maxLength);
            }
            
            if (minimum != null) {
                sb.append(",\"minimum\":").append(minimum);
            }
            
            if (maximum != null) {
                sb.append(",\"maximum\":").append(maximum);
            }
            
            if (type == SchemaType.ARRAY && items != null) {
                sb.append(",\"items\":").append(items.toJSON());
            }
            
            if (nestedSchema != null) {
                sb.append(",\"properties\":")
                  .append(extractProperties(nestedSchema));

                if (!nestedSchema.required().isEmpty()) {
                    sb.append(",\"required\":")
                      .append(toJsonArray(nestedSchema.required()));
                }
            }
            sb.append("}");
            return sb.toString();
        }
        
        private static String toJsonArray(Collection<String> values) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String v : values) {
                if (!first) sb.append(",");
                sb.append("\"").append(v).append("\"");
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }

        private static String extractProperties(JSONSchema schema) {
            String json = schema.toJSON();
            int start = json.indexOf("\"properties\":");
            if (start == -1) return "{}";

            int brace = json.indexOf("{", start);
            int end = findMatchingBrace(json, brace);
            return json.substring(brace, end + 1);
        }

        private static int findMatchingBrace(String str, int start) {
            int depth = 0;
            for (int i = start; i < str.length(); i++) {
                char c = str.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            throw new IllegalStateException("Unbalanced JSON braces");
        }
    }
}
enum SchemaType {
	STRING("string"),
	NUMBER("number"),
	BOOLEAN("boolean"),
	OBJECT("object"),
	ARRAY("array");

	private final String json;

	SchemaType(String json) {
		this.json = json;
	}

	public String json() {
		return json;
	}
}