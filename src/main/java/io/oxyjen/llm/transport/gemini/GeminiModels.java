package io.oxyjen.llm.transport.gemini;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.Models;

/**
* Factory for creating Gemini ChatModels.
* 
* They access it via LLM.of("gemini/gemini-flash-latest") which calls this.
* 
* Responsibilities:
* - Read API key from environment
* - Validate model name
* - Create GeminiChatModel instances
* 
* This is the glue between LLM.of() and GeminiChatModel.
*/
public final class GeminiModels {

    private static final String GEMINI_API_KEY = "GEMINI_API_KEY";

    public static ChatModel create(String modelName) {
        String apiKey = resolveApiKey();
        return create(modelName, apiKey, null);
    }

    /**
     * Create Gemini ChatModel with explicit API key.
     */
    public static ChatModel create(String modelName, String apiKey) {
        return create(modelName, apiKey, null);
    }

    /**
     * Create Gemini ChatModel with explicit API key and timeout.
     */
    public static ChatModel create(String modelName, String apiKey, Duration requestTimeout) {
        if (!Models.isSupported(modelName)) {
            throw new IllegalArgumentException(
                "Unknown Gemini model: " + modelName + "\n" +
                "Supported: " + String.join(", ", Models.getSupportedModels())
            );
        }
        
        // Strip "gemini/" prefix if present: "gemini/gemini-2.0-flash" -> "gemini-2.0-flash"
        String geminiModelName = modelName.startsWith("gemini/")
            ? modelName.substring("gemini/".length())
            : modelName;

        if (requestTimeout != null) {
            return new GeminiChatModel(apiKey, geminiModelName, requestTimeout);
        }
        return new GeminiChatModel(apiKey, geminiModelName);
    }

    private static String resolveApiKey() {
    	String apiKey = System.getProperty("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
        	apiKey = System.getenv(GEMINI_API_KEY);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY environment variable not set.\n" +
                "Get your free API key from: https://aistudio.google.com/apikey\n" +
                "Then set it:\n" +
                "  Windows: set GEMINI_API_KEY=your-key-here\n" +
                "  Linux/Mac: export GEMINI_API_KEY=your-key-here\n" +
                "Or pass it explicitly:\n" +
                "  LLM.gemini(\"gemini-2.0-flash\", \"your-key-here\")"
            );
        }
        return apiKey;
    }

    private GeminiModels() {}
}