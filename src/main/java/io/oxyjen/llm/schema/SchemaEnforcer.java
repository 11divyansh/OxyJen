package io.oxyjen.llm.schema;

import java.util.List;

import io.oxyjen.llm.ChatModel;

/**
 * Enforces JSON schema by retrying until valid output.
 * 
 * Strategy:
 * 1. Call LLM with schema instructions
 * 2. Validate response
 * 3. If invalid, retry with error feedback
 * 4. Max retries before giving up
 */
public final class SchemaEnforcer {
    
    private final ChatModel model;
    private final JSONSchema schema;
    private final int maxRetries;
    
    public SchemaEnforcer(ChatModel model, JSONSchema schema, int maxRetries) {
        this.model = model;
        this.schema = schema;
        this.maxRetries = maxRetries;
    }
    
    public SchemaEnforcer(ChatModel model, JSONSchema schema) {
        this(model, schema, 3);
    }
    
    /**
     * Execute prompt with schema enforcement.
     * 
     * @return Valid JSON string
     * @throws SchemaException if all retries fail
     */
    public String execute(String prompt) {
        SchemaValidator validator = new SchemaValidator(schema);
        
        // Add schema instruction to prompt
        String enhancedPrompt = buildPromptWithSchema(prompt);
        
        String lastResponse = null;
              
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Call LLM
            String response = model.chat(enhancedPrompt);
            lastResponse = response;
            
            // Extract JSON (remove markdown fences if present)
            String json = extractJSON(response);
            
            // Validate
            SchemaValidator.ValidationResult result = validator.validate(json);
            
            if (result.isValid()) {
                return json; // Success!
            }
            
            // Build error feedback for retry
            //astErrors = String.join("\n", result.errors());
            enhancedPrompt = buildRetryPrompt(prompt, json, result,attempt);
        }
        
        throw new SchemaException(
            "Failed to get valid JSON after " + maxRetries + " attempts",
            lastResponse
        );
    }
    
    private String buildPromptWithSchema(String userPrompt) {
        return userPrompt + "\n\n" +
               "IMPORTANT:\n"+
        	   "Return ONLY a raw JSON object matching this schema:\n" +
               "Do not include any explanations, just the JSON. No markdown. \n\n" +
               schema.toJSON();
    }
    
    private String buildRetryPrompt(
    		String originalPrompt, 
    		String invalidJSON, SchemaValidator.ValidationResult result,
    		int attempt
    	) {
    	return originalPrompt + "\n\n" +
                "Attempt " + attempt + " failed.\n\n" +
                "Your previous response did NOT match the required JSON schema.\n\n" +
                "Previous JSON:\n" + invalidJSON + "\n\n" +
                "Schema violations:\n" + result.formatErrors() + "\n\n" +
                "Please return ONLY corrected JSON matching this schema:\n" +
                schema.toJSON();
    }
    
    private String extractJSON(String response) {
        // Remove markdown code fences if present
        String cleaned = response.trim();
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        return cleaned.trim();
    }
}