package io.oxyjen.llm.schema.annotations;

import java.lang.annotation.*;

/**
 * Regex pattern validation for strings.
 * 
 * Usage:
 * <pre>
 * @Pattern("^[a-z]+$")
 * String username
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface Pattern {
    String value();
}