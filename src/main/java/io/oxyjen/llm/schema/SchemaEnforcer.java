package io.oxyjen.llm.schema;

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
    private final String schemaJson;
    
    public SchemaEnforcer(ChatModel model, JSONSchema schema, int maxRetries) {
        this.model = model;
        this.schema = schema;
        this.maxRetries = maxRetries;
        this.schemaJson = schema.toJSON();
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
        String currentPrompt = buildInitialPrompt(prompt);
        
        String lastResponse = null;
              
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String response = model.chat(currentPrompt);
            lastResponse = response;
            
            String json;
            try {
            	json = extractJSON(response);
            } catch (Exception extractionError) {
            	currentPrompt = buildRetryPrompt(
            			prompt,
            			response,
            			"Could not extract valid JSON object from response.",
            			attempt);
            	continue;
            }
            SchemaValidator.ValidationResult result = validator.validate(json);          
            if (result.isValid()) {
                return json;
            }            
            currentPrompt = buildRetryPrompt(prompt, json, result.formatErrors(),attempt);
        }       
        throw new SchemaException(
            "Failed to get valid JSON after " + maxRetries + " attempts",
            lastResponse
        );
    }
    
    private String buildInitialPrompt(String userPrompt) {
        return userPrompt + "\n\n" +
                "You MUST return a valid JSON object.\n" +
                "Do NOT include explanations.\n" +
                "Do NOT include markdown.\n" +
                "Do NOT wrap the response in code blocks.\n" +
                "Return ONLY raw JSON.\n\n" +
                "The JSON must match this schema exactly:\n\n" +
                schemaJson;
    }
    
    private String buildRetryPrompt(
    		String originalPrompt, 
    		String previousOutput, String validationErrors,
    		int attempt
    	) {
    	return originalPrompt + "\n\n" +
                "Attempt " + attempt + " failed.\n\n" +
                "Your previous response did NOT match the required JSON schema.\n\n" +
                "Previous JSON:\n" + previousOutput + "\n\n" +
                "Schema violations:\n" + validationErrors + "\n\n" +
                "You MUST correct the JSON.\n" +
                "Return ONLY valid JSON.\n" +
                "No explanations.\n" +
                "No markdown.\n\n" +
                "Please return ONLY corrected JSON matching this schema:\n" +
                schemaJson;
    }
    
    private String extractJSON(String response) {

    	if (response == null || response.isBlank()) {
    		throw new IllegalArgumentException("Empty response from model");
    	}
        String cleaned = response.trim();
        
        if (cleaned.startsWith("```")) {
        	int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("No JSON object found in model response");
        }
        return cleaned.substring(start, end+1).trim();
    }
}