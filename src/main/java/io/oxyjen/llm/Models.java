package io.oxyjen.llm;

import java.util.HashMap;
import java.util.Map;

import io.oxyjen.llm.models.ModelInfo;

/**
 * This is pure data, no logic.
 * 
 * Responsibilities:
 * - Define available models
 * - Provide metadata (context length, etc.)
 */
public final class Models {
    
    public static final String GPT_4O = "gpt-4o";
    public static final String GPT_4O_MINI = "gpt-4o-mini";
    public static final String GPT_41_NANO = "gpt-4.1-nano";
    public static final String GPT_4_TURBO = "gpt-4-turbo";
    public static final String GPT_4 = "gpt-4";
    public static final String GPT_35_TURBO = "gpt-3.5-turbo";
    
    public static final String OLLAMA_LLAMA3 = "ollama/llama3";
    public static final String OLLAMA_LLAMA3_LATEST = "ollama/llama3:latest";
    public static final String OLLAMA_MISTRAL = "ollama/mistral";
    public static final String OLLAMA_GEMMA2 = "ollama/gemma2";
    public static final String OLLAMA_PHI3 = "ollama/phi3";
    public static final String OLLAMA_DEEPSEEK = "ollama/deepseek-r1";
    
    private static final Map<String, ModelInfo> MODELS = new HashMap<>();
    
    static {
        // GPT-4o (2024-11-20 snapshot)
        MODELS.put(GPT_4O, new ModelInfo(GPT_4O, 128_000));
        
        // GPT-4o-mini
        MODELS.put(GPT_4O_MINI, new ModelInfo(GPT_4O_MINI, 128_000));

        // GPT-4.1 nano
        MODELS.put(GPT_41_NANO, new ModelInfo(GPT_41_NANO, 1_047_576));
        
        // GPT-4-turbo
        MODELS.put(GPT_4_TURBO, new ModelInfo(GPT_4_TURBO,128_000));
        
        // GPT-4 (older)
        MODELS.put(GPT_4, new ModelInfo(GPT_4, 8_192));
        
        // GPT-3.5-turbo
        MODELS.put(GPT_35_TURBO, new ModelInfo(GPT_35_TURBO, 16_385));
        
        MODELS.put(OLLAMA_LLAMA3,    new ModelInfo(OLLAMA_LLAMA3,   128_000));
        MODELS.put(OLLAMA_MISTRAL,   new ModelInfo(OLLAMA_MISTRAL,    32_000));
        MODELS.put(OLLAMA_GEMMA2,    new ModelInfo(OLLAMA_GEMMA2,     8_192));
        MODELS.put(OLLAMA_PHI3,      new ModelInfo(OLLAMA_PHI3,      128_000));
        MODELS.put(OLLAMA_DEEPSEEK,  new ModelInfo(OLLAMA_DEEPSEEK,  128_000));
        MODELS.put(OLLAMA_LLAMA3_LATEST, new ModelInfo(OLLAMA_LLAMA3_LATEST, 128_000));
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