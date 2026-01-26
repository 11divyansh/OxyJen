package io.oxyjen.llm.prompts;

/**
 * Represents a template variable with validation.
 */
public final class Variable {
    
    private final String name;
    private final boolean required;
    private final String defaultValue;
    //private final String description;
    
    private Variable(String name, boolean required, String defaultValue) {
        this.name = name;
        this.required = required;
        this.defaultValue = defaultValue;
    }
    
    public static Variable required(String name) {
        return new Variable(name, true, null);
    }
    
    public static Variable optional(String name, String defaultValue) {
        return new Variable(name, false, defaultValue);
    }
    
    public String name() {
        return name;
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public String defaultValue() {
        return defaultValue;
    }
}