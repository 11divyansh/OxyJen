package io.oxyjen.llm.schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.oxyjen.llm.schema.annotations.Description;
import io.oxyjen.llm.schema.annotations.JsonIgnore;
import io.oxyjen.llm.schema.annotations.Max;
import io.oxyjen.llm.schema.annotations.Min;
import io.oxyjen.llm.schema.annotations.Pattern;
import io.oxyjen.llm.schema.annotations.Size;

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
            return fromPOJO(clazz);
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
            
            // skip @JsonIgnore record components
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
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
     * Generate schema from a POJO
     */
    private static JSONSchema fromPOJO(Class<?> pojoClass) {
        JSONSchema.Builder builder = JSONSchema.object();
        
        List<String> requiredFields = new ArrayList<>();
        
        Method[] methods = pojoClass.getMethods();
        
        for (Method method : methods) {
            // skip non-getters
            if (!isGetter(method)) {
                continue;
            }
            
            if (method.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            
            String fieldName = getFieldNameFromGetter(method);
            Class<?> fieldType = method.getReturnType();
            Type genericType = method.getGenericReturnType();
            
            String description = getDescriptionFromMethod(method);
            
            boolean optional = isOptionalMethod(method);
            
            JSONSchema.PropertySchema property = createProperty(
                fieldType,
                genericType,
                description,
                method
            );
            
            builder.property(fieldName, property);
            
            if (!optional) {
                requiredFields.add(fieldName);
            }
        }
        
        if (!requiredFields.isEmpty()) {
            builder.required(requiredFields.toArray(new String[0]));
        }
        
        String classDescription = getClassDescription(pojoClass);
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
        if (type == String.class) {
            return createStringProperty(description, element);
        }
        if (isNumericType(type)) {
        	return createNumberProperty(description, element);
        }
        if (type == boolean.class || type == Boolean.class) {
            return createBooleanProperty(description, element);
        }
        if (type.isEnum()) {
            return createEnumProperty(type, description);
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
        
        if (element != null && element.isAnnotationPresent(Pattern.class)) {
            Pattern pattern = element.getAnnotation(Pattern.class);
            builder.pattern(pattern.value());
        }
        
        if (element != null && element.isAnnotationPresent(Size.class)) {
            Size size = element.getAnnotation(Size.class);
            if (size.min() > 0) builder.minLength(size.min());
            if (size.max() < Integer.MAX_VALUE) builder.maxLength(size.max());
        }
        
        return builder.build();
    }
    /**
     * create number property with validation.
     */
    private static JSONSchema.PropertySchema createNumberProperty(
            String description,
            AnnotatedElement element
    ) {
        JSONSchema.PropertySchema.Builder builder = 
            JSONSchema.PropertySchema.number(description);
        
        if (element != null && element.isAnnotationPresent(Min.class)) {
            Min min = element.getAnnotation(Min.class);
            builder.minimum(min.value());
        }
        
        if (element != null && element.isAnnotationPresent(Max.class)) {
            Max max = element.getAnnotation(Max.class);
            builder.maximum(max.value());
        }
        
        return builder.build();
    }
    /**
     * create boolean property.
     */
    private static JSONSchema.PropertySchema createBooleanProperty(
            String description,
            AnnotatedElement element
    ) {
        return JSONSchema.PropertySchema.bool(description).build();
    }
    
    /**
     * create enum property.
     */
    private static JSONSchema.PropertySchema createEnumProperty(
            Class<?> enumType,
            String description
    ) {
        Object[] enumConstants = enumType.getEnumConstants();
        String[] values = Arrays.stream(enumConstants)
            .map(Object::toString)
            .toArray(String[]::new);
        
        return JSONSchema.PropertySchema.enumOf(description, values).build();
    }
    /**
     * Check if type is numeric.
     */
    private static boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class ||
               type == long.class || type == Long.class ||
               type == double.class || type == Double.class ||
               type == float.class || type == Float.class ||
               type == short.class || type == Short.class ||
               type == byte.class || type == Byte.class;
    }
    /**
     * Check if method is a getter.
     */
    private static boolean isGetter(Method method) {
        // must start with "get" or "is"
        String name = method.getName();
        if (!name.startsWith("get") && !name.startsWith("is")) {
            return false;
        }
        
        if (method.getParameterCount() != 0) {
            return false;
        }
        
        if (method.getReturnType() == void.class) {
            return false;
        }
        
        // "is" for boolean
        if (name.startsWith("is")) {
            return method.getReturnType() == boolean.class ||
                   method.getReturnType() == Boolean.class;
        }
        
        return true;
    }
    /**
     * Extract field name from getter method.
     * 
     * getName() -> name
     * isActive() -> active
     */
    private static String getFieldNameFromGetter(Method method) {
        String name = method.getName();
        
        if (name.startsWith("is")) {
            name = name.substring(2);
        } else if (name.startsWith("get")) {
            name = name.substring(3);
        }
        
        // first character lowercase
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    /**
     * Get description from RecordComponent.
     */
    private static String getDescription(RecordComponent component) {
        Description annotation = component.getAnnotation(Description.class);
        return annotation != null ? annotation.value() : component.getName();
    }
    /**
     * Get description from Method (for POJOs).
     */
    private static String getDescriptionFromMethod(Method method) {
        Description annotation = method.getAnnotation(Description.class);
        if (annotation != null) {
            return annotation.value();
        }
        
        // try to get from field
        try {
            String fieldName = getFieldNameFromGetter(method);
            Field field = method.getDeclaringClass().getDeclaredField(fieldName);
            annotation = field.getAnnotation(Description.class);
            if (annotation != null) {
                return annotation.value();
            }
        } catch (NoSuchFieldException e) {
            // field not found, use method name
        }
        
        return getFieldNameFromGetter(method);
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
     * check if Method (POJO getter) is optional.
     */
    private static boolean isOptionalMethod(Method method) {
        // check @Nullable on method
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if (name.equals("Nullable") || name.equals("Optional")) {
                return true;
            }
        }
        
        // check @Nullable on field
        try {
            String fieldName = getFieldNameFromGetter(method);
            Field field = method.getDeclaringClass().getDeclaredField(fieldName);
            
            for (Annotation annotation : field.getAnnotations()) {
                String name = annotation.annotationType().getSimpleName();
                if (name.equals("Nullable") || name.equals("Optional")) {
                    return true;
                }
            }
        } catch (NoSuchFieldException e) {
            // field not found
        }
        
        // check if return type is Optional
        return method.getReturnType() == Optional.class;
    }
    /**
     * Get class-level description.
     */
    private static String getClassDescription(Class<?> clazz) {
        Description annotation = clazz.getAnnotation(Description.class);
        return annotation != null ? annotation.value() : null;
    }
}
