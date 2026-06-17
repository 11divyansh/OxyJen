package io.oxyjen.llm.transport.ollama;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;

public final class OllamaModels {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String ENV_BASE_URL = "OLLAMA_BASE_URL";

    public static ChatModel create(String modelName) {
        return create(modelName, resolveBaseUrl(), null);
    }

    public static ChatModel create(String modelName, String baseUrl) {
        return create(modelName, baseUrl, null);
    }

    public static ChatModel create(String modelName, String baseUrl, Duration requestTimeout) {
        String ollamaModelName = modelName.startsWith("ollama/")
            ? modelName.substring("ollama/".length())
            : modelName;

        System.out.println("[OllamaModels] Resolved model name: " + ollamaModelName);
        if (requestTimeout != null) {
            return new OllamaChatModel(ollamaModelName, baseUrl, requestTimeout);
        }
        return new OllamaChatModel(ollamaModelName, baseUrl);
    }

    private static String resolveBaseUrl() {
        String env = System.getenv("OLLAMA_BASE_URL");
        return (env != null && !env.isBlank()) ? env : DEFAULT_BASE_URL;
    }

    private OllamaModels() {}
}