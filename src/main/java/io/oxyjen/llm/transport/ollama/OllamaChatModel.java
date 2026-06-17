package io.oxyjen.llm.transport.ollama;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;

/* Ollama implementation of ChatModel.
* 
* This wraps OllamaClient and implements the simple ChatModel interface.
* It translates the simple chat(String) call into Ollama's message format.
*/
public final class OllamaChatModel implements ChatModel {

    private final OllamaClient client;
    private final String model;

    private Double temperature;
    private Integer maxTokens;
    private TokenUsage lastUsage;

    public OllamaChatModel(String model) {
        this.client = new OllamaClient();
        this.model = model;
    }
    
    public OllamaChatModel(String model, String baseUrl) {
        this.client = new OllamaClient(baseUrl);
        this.model = model;
    }

    /**
     * Use this if Ollama is running on a non-default host/port.
     * e.g., remote machine or Docker: "http://192.168.1.10:11434"
     */
    public OllamaChatModel(String model, String baseUrl, Duration requestTimeout) {
        this.client = new OllamaClient(baseUrl, requestTimeout);
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

    public OllamaChatModel withTemperature(double temp) {
        this.temperature = temp;
        return this;
    }

    public OllamaChatModel withMaxTokens(int tokens) {
        this.maxTokens = tokens;
        return this;
    }

    public TokenUsage getLastUsage() { return lastUsage; }
    public String getModelName() { return model; }
}