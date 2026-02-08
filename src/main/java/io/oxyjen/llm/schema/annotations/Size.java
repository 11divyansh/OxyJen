package io.oxyjen.llm.schema.annotations;

import java.lang.annotation.*;

/**
 * Size constraints for strings or collections.
 * 
 * Usage:
 * <pre>
 * @Size(min = 3, max = 50)
 * String username
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface Size {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
}