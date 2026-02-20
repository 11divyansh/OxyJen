package io.oxyjen.llm.prompts;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for reusable prompt templates.
 * 
 * Allows you to:
 * - Define prompts once
 * - Reuse across application
 */
public final class PromptRegistry {
    
    private static final Map<String, PromptTemplate> REGISTRY = new ConcurrentHashMap<>();
    private PromptRegistry() {}

    public static void register(String name, PromptTemplate template) {
    	validateName(name);
    	validateTemplate(template);
        if (REGISTRY.putIfAbsent(name, template) != null) {
            throw new IllegalStateException("Prompt already registered: " + name);
        }
    }
    public static void register(String name, String templateString) {
    	validateName(name);
    	if (templateString == null) {
            throw new IllegalArgumentException("Template string cannot be null");
        }
        register(name, PromptTemplate.of(templateString));
    }
    public static void registerOrReplace(String name, PromptTemplate template) {
        validateName(name);
        validateTemplate(template);
        REGISTRY.put(name, template);
    }
    public static PromptTemplate get(String name) {
    	validateName(name);
        PromptTemplate template = REGISTRY.get(name);
        if (template == null) {
            throw new IllegalArgumentException("Prompt not found: " + name);
        }
        return template;
    }
    public static boolean exists(String name) {
        return REGISTRY.containsKey(name);
    } 
    public static Set<String> getAllNames() {
        return new HashSet<>(REGISTRY.keySet());
    }
    public static void clear() {
        REGISTRY.clear();
    }
    private static void validateName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Prompt name cannot be null or blank");
    }
    private static void validateTemplate(PromptTemplate template) {
        if (template == null)
            throw new IllegalArgumentException("Prompt template cannot be null");
    }
}