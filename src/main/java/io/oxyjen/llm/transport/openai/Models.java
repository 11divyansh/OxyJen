package io.oxyjen.llm.transport.openai;



import java.util.HashMap;
import java.util.Map;

/**
 * OpenAI model definitions.
 * 
 * This is pure data, no logic.
 * 
 * Responsibilities:
 * - Define available models
 * - Provide metadata (context length, etc.)
 */
public final class Models {
    
    public static final String GPT_4O = "gpt-4o";
    public static final String GPT_4O_MINI = "gpt-4o-mini";
    public static final String GPT_4_TURBO = "gpt-4-turbo";
    public static final String GPT_4 = "gpt-4";
    public static final String GPT_35_TURBO = "gpt-3.5-turbo";
    
    private static final Map<String, ModelInfo> MODELS = new HashMap<>();
    
    static {
        // GPT-4o (2024-11-20 snapshot)
        MODELS.put(GPT_4O, new ModelInfo(
            GPT_4O,
            128_000 // 128k context window
        ));
        
        // GPT-4o-mini
        MODELS.put(GPT_4O_MINI, new ModelInfo(
            GPT_4O_MINI,
            128_000
        ));
        
        // GPT-4-turbo
        MODELS.put(GPT_4_TURBO, new ModelInfo(
            GPT_4_TURBO,
            128_000
        ));
        
        // GPT-4 (older)
        MODELS.put(GPT_4, new ModelInfo(
            GPT_4,
            8_192
        ));
        
        // GPT-3.5-turbo
        MODELS.put(GPT_35_TURBO, new ModelInfo(
            GPT_35_TURBO,
            16_385
        ));
    }
    
    /**
     * Get model information.
     * 
     * @param modelName Model name (e.g., "gpt-4o")
     * @return ModelInfo or null if unknown
     */
    public static ModelInfo getInfo(String modelName) {
        return MODELS.get(modelName);
    }
    
    /**
     * Check if model is supported.
     */
    public static boolean isSupported(String modelName) {
        return MODELS.containsKey(modelName);
    }
    
    /**
     * Get all supported model names.
     */
    public static String[] getSupportedModels() {
        return MODELS.keySet().toArray(new String[0]);
    }
    
    private Models() {
        // No instances
    }
}

/**
 * Model metadata.
 */
record ModelInfo(
    String name,
    int contextLength              // Max tokens in context window
) {}