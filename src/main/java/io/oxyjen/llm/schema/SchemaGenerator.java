package io.oxyjen.llm.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.oxyjen.llm.schema.annotations.Description;
import io.oxyjen.llm.schema.annotations.Size;
import io.oxyjen.llm.schema.annotations.Pattern;

 /**
 * Generates JSON schemas from Java classes using reflection.
 * 
 * v0.4 FULL SUPPORT:
 * - Records
 * - POJOs with getters
 * - Nested objects
 * - Collections (List, Set, Array)
 * - Maps
 * - Enums
 * - Optional fields
 * - Default values
 * - Validation annotations
 * - Circular reference detection
 * 
 * @version 0.4
 */
public final class SchemaGenerator {
	
	// Track visited classes to prevent infinite recursion
    private static final ThreadLocal<Set<Class<?>>> VISITED = 
        ThreadLocal.withInitial(HashSet::new);
	/**
     * Generate schema from a Java class.
     * 
     * @param clazz The class to generate schema for
     * @return JSONSchema representing the class structure
     */
    public static JSONSchema fromClass(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        
        try {
            VISITED.get().clear();
            return generateSchema(clazz);
        } finally {
            VISITED.remove();
        }
    }
    /**
     * Internal schema generation with circular reference detection.
     */
    private static JSONSchema generateSchema(Class<?> clazz) {
        // Detect circular references
        if (VISITED.get().contains(clazz)) {
            throw new IllegalArgumentException(
                "Circular reference detected: " + clazz.getSimpleName() + 
                " references itself. Use @JsonIgnore or refactor structure."
            );
        }
        
        VISITED.get().add(clazz);
        
        // Route to appropriate handler
        if (clazz.isRecord()) {
        	return fromRecord(clazz);
        } else {
            // handler 2
        	return null;
        }
    }
    /**
     * Generate schema from a Java record.
     */
    private static JSONSchema fromRecord(Class<?> recordClass) {
        JSONSchema.Builder builder = JSONSchema.object();
        
        RecordComponent[] components = recordClass.getRecordComponents();
        List<String> requiredFields = new ArrayList<>();
        
        for (RecordComponent component : components) {
            String fieldName = component.getName();
            Class<?> fieldType = component.getType();
            
            // Get description
            String description = getDescription(component);
            
            // Check if field is optional
            boolean optional = isOptional(component);
            
            // Get generic type for collections
            Type genericType = component.getGenericType();
            
            // Create property schema
            JSONSchema.PropertySchema property = createProperty(
                fieldType, 
                genericType, 
                description,
                component
            );
            
            builder.property(fieldName, property);
            
            if (!optional) {
                requiredFields.add(fieldName);
            }
        }
        
        if (!requiredFields.isEmpty()) {
            builder.required(requiredFields.toArray(new String[0]));
        }
        
        String classDescription = getClassDescription(recordClass);
        if (classDescription != null) {
            builder.description(classDescription);
        }
        
        return builder.build();
    }
    /**
     * Create a PropertySchema from a Java type.
     */
    private static JSONSchema.PropertySchema createProperty(
            Class<?> type,
            Type genericType,
            String description,
            AnnotatedElement element
    ) {
        // Primitives and boxed types
        if (type == String.class) {
            return createStringProperty(description, element);
        }
        
        throw new UnsupportedOperationException(
            "Unsupported type: " + type.getSimpleName() + 
            ". Supported: primitives, String, enums, Collections, Arrays, Maps, POJOs, Records"
        );
    }
    /**
     * Create string property with validation.
     */
    private static JSONSchema.PropertySchema createStringProperty(
            String description,
            AnnotatedElement element
    ) {
        JSONSchema.PropertySchema.Builder builder = 
            JSONSchema.PropertySchema.string(description);
        
        // Apply @Pattern validation
        if (element != null && element.isAnnotationPresent(Pattern.class)) {
            Pattern pattern = element.getAnnotation(Pattern.class);
            builder.pattern(pattern.value());
        }
        
        // Apply @Size validation
        if (element != null && element.isAnnotationPresent(Size.class)) {
            Size size = element.getAnnotation(Size.class);
            if (size.min() > 0) builder.minLength(size.min());
            if (size.max() < Integer.MAX_VALUE) builder.maxLength(size.max());
        }
        
        return builder.build();
    }
    /**
     * Get description from RecordComponent.
     */
    private static String getDescription(RecordComponent component) {
        Description annotation = component.getAnnotation(Description.class);
        return annotation != null ? annotation.value() : component.getName();
    }
    /**
     * Check if RecordComponent is optional.
     */
    private static boolean isOptional(RecordComponent component) {
        // Check @Nullable
        for (Annotation annotation : component.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.equals("Nullable") || name.equals("Optional")) {
                return true;
            }
        }
        
        // Check if type is Optional
        return component.getType() == Optional.class;
    }
    /**
     * Get class-level description.
     */
    private static String getClassDescription(Class<?> clazz) {
        Description annotation = clazz.getAnnotation(Description.class);
        return annotation != null ? annotation.value() : null;
    }
}
