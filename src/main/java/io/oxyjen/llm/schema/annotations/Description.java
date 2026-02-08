package io.oxyjen.llm.schema.annotations;

import java.lang.annotation.*;

/**
 * Provides descriptions for schema generation.
 * 
 * Usage:
 * <pre>
 * @Description("Customer information")
 * public record Customer(
 *     @Description("Full name of the customer") String name,
 *     @Description("Age in years") int age
 * ) {}
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.RECORD_COMPONENT, ElementType.FIELD})
public @interface Description {
    String value();
}