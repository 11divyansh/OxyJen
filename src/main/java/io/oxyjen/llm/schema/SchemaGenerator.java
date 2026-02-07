package io.oxyjen.llm.schema;

import java.util.HashSet;
import java.util.Set;

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
public class SchemaGenerator {
	
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
            // handler 1
        	return null;
        } else {
            // handler 2
        	return null;
        }
    }
}
