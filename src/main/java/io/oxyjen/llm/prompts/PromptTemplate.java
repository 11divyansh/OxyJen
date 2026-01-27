package io.oxyjen.llm.prompts;

import java.util.*;
import java.util.regex.*;

/**
 * Template engine for prompts with variable substitution.
 * 
 * Syntax: {{variable_name}}
 * 
 * Example:
 *   "Hello {{name}}, you are {{age}} years old"
 */
public final class PromptTemplate {
    
    private final String template;
    private final Set<String> variableNames;
    private final Map<String, Variable> variables;
    
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");
    
    private PromptTemplate(String template, Map<String, Variable> variables) {
        this.template = template;
        this.variables = new HashMap<>(variables);
        this.variableNames = extractVariables(template);
        
        // Validate that all template variables are defined
        validateVariables();
    }
    
    /**
     * Create a template from string.
     */
    public static PromptTemplate of(String template) {
        return new PromptTemplate(template, new HashMap<>());
    }
    
    /**
     * Create a template with variable definitions.
     */
    public static PromptTemplate of(String template, Variable... variables) {
        Map<String, Variable> varMap = new HashMap<>();
        for (Variable var : variables) {
            varMap.put(var.name(), var);
        }
        return new PromptTemplate(template, varMap);
    }
    
    /**
     * Render the template with provided values.
     * 
     * @throws TemplateException if required variables are missing
     */
    public String render(Map<String, Object> values) {
        Map<String, Object> allValues = new HashMap<>(values);
        
        // Add default values for optional variables
        for (Variable var : variables.values()) {
            if (!var.isRequired() && !allValues.containsKey(var.name())) {
                allValues.put(var.name(), var.defaultValue());
            }
        }
        
        // Validate required variables
        for (Variable var : variables.values()) {
            if (var.isRequired() && !allValues.containsKey(var.name())) {
                throw new TemplateException(
                    "Missing required variable: " + var.name(),
                    "Template: " + template
                );
            }
        }
        
        // Replace all variables
        String result = template;
        for (Map.Entry<String, Object> entry : allValues.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        
        return result;
    }
    
    /**
     * Render with varargs syntax.
     */
    public String render(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Must provide key-value pairs");
        }
        
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = keyValuePairs[i].toString();
            Object value = keyValuePairs[i + 1];
            values.put(key, value);
        }
        
        return render(values);
    }
    
    /**
     * Get all variable names in the template.
     */
    public Set<String> getVariables() {
        return new HashSet<>(variableNames);
    }
    
    private Set<String> extractVariables(String template) {
        Set<String> vars = new HashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(template);
        
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        
        return vars;
    }
    
    private void validateVariables() {
        // Check if all template variables have definitions
        for (String varName : variableNames) {
            if (!variables.containsKey(varName)) {
                // Auto-create as optional with empty default
                variables.put(varName, Variable.optional(varName, ""));
            }
        }
    }
}