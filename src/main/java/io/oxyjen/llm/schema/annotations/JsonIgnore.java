package io.oxyjen.llm.schema.annotations;

import java.lang.annotation.*;

/**
 * Exclude field/method from schema generation.
 * 
 * Usage:
 * <pre>
 * public class User {
 *     @JsonIgnore
 *     public String getPassword() { ... }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface JsonIgnore {
}