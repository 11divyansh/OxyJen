package io.oxyjen.llm.transport.gemini;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;

/**
 * Gemini implementation of ChatModel.
 * 
 * This wraps GeminiClient and implements the simple ChatModel interface.
 * It translates the simple chat(String) call into Gemini's message format.
 * 
 * Responsibilities:
 * - Implement ChatModel.chat()
 * - Build Gemini request objects
 * - Delegate to GeminiClient for HTTP
 * - Return clean string responses
 * 
 * What it does not do:
 * - HTTP logic (delegates to GeminiClient)
 * - Memory management (that's NodeContext)
 * - Graph execution (that's Executor)
 */
public final class GeminiChatModel implements ChatModel {

    private final GeminiClient client;
    private final String model;

    // Configuration (optional, can be null)
    private Double temperature;
    private Integer maxTokens;
    
    // Last call metadata (for cost tracking, debugging)
    private TokenUsage lastUsage;

    /**
     * Create Gemini chat model.
     * 
     * @param apiKey Gemini API key
     * @param model Model name (e.g., "gemini/gemini-flash-latest")
     */
    public GeminiChatModel(String apiKey, String model) {
        this.client = new GeminiClient(apiKey);
        this.model = model;
    }

    public GeminiChatModel(String apiKey, String model, Duration requestTimeout) {
        this.client = new GeminiClient(apiKey, requestTimeout);
        this.model = model;
    }

    @Override
    public String chat(String input) {
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
            .model(model)
            .addMessage("user", input);

        if (temperature != null) requestBuilder.temperature(temperature);
        if (maxTokens != null) requestBuilder.maxTokens(maxTokens);

        ChatRequest request = requestBuilder.build();
        ChatResponse response = client.chat(request);
        this.lastUsage = response.usage();
        return response.content();
    }

    public GeminiChatModel withTemperature(double temp) {
        this.temperature = temp;
        return this;
    }

    /**
     * Set max tokens in response.
     */
    public GeminiChatModel withMaxTokens(int tokens) {
        this.maxTokens = tokens;
        return this;
    }

    /**
     * Get token usage from last call.
     * Useful for cost tracking and debugging.
     */
    public TokenUsage getLastUsage() { return lastUsage; }
    
    /**
     * Get model name.
     */
    public String getModelName() { return model; }
}