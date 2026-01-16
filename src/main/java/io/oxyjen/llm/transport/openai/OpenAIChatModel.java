package io.oxyjen.llm.transport.openai;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;

/**
 * OpenAI implementation of ChatModel.
 * 
 * This wraps OpenAIClient and implements the simple ChatModel interface.
 * It translates the simple chat(String) call into OpenAI's message format.
 * 
 * Responsibilities:
 * - Implement ChatModel.chat()
 * - Build OpenAI request objects
 * - Delegate to OpenAIClient for HTTP
 * - Return clean string responses
 * 
 * What it does NOT do:
 * - HTTP logic (delegates to OpenAIClient)
 * - Memory management (that's NodeContext)
 * - Graph execution (that's Executor)
 */
public final class OpenAIChatModel implements ChatModel {
    
    private final OpenAIClient client;
    private final String model;
    
    // Configuration (optional, can be null)
    private Double temperature;
    private Integer maxTokens;
    
    // Last call metadata (for cost tracking, debugging)
    private TokenUsage lastUsage;
    
    /**
     * Create OpenAI chat model.
     * 
     * @param apiKey OpenAI API key
     * @param model Model name (e.g., "gpt-4o", "gpt-4o-mini")
     */
    public OpenAIChatModel(String apiKey, String model) {
        this.client = new OpenAIClient(apiKey);
        this.model = model;
    }
    
    @Override
    public String chat(String input) {
        // Build request
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
            .model(model)
            .addMessage("user", input);
        
        if (temperature != null) {
            requestBuilder.temperature(temperature);
        }
        if (maxTokens != null) {
            requestBuilder.maxTokens(maxTokens);
        }
        
        ChatRequest request = requestBuilder.build();
        
        // Call OpenAI
        ChatResponse response = client.chat(request);
        
        // Store metadata for cost tracking
        this.lastUsage = response.usage();
        
        // Return content
        return response.content();
    }
    
    // config method for v0.3+    
    /**
     * Set temperature (0.0 to 2.0).
     * Controls randomness - lower is more deterministic.
     * 
     * NOTE: This doesn't fit ChatModel interface yet.
     * For v0.2, we keep ChatModel minimal.
     * These will be used via builder pattern in v0.3.
     */
    public OpenAIChatModel withTemperature(double temp) {
        this.temperature = temp;
        return this;
    }
    
    /**
     * Set max tokens in response.
     */
    public OpenAIChatModel withMaxTokens(int tokens) {
        this.maxTokens = tokens;
        return this;
    }
    
    /**
     * Get token usage from last call.
     * Useful for cost tracking and debugging.
     */
    public TokenUsage getLastUsage() {
        return lastUsage;
    }
    
    /**
     * Get model name.
     */
    public String getModelName() {
        return model;
    }
}