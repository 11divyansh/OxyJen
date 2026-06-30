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
    
    public static final String GEMINI_2_FLASH      = "gemini/gemini-2.0-flash";
    public static final String GEMINI_FLASH_LATEST = "gemini/gemini-flash-latest";
    public static final String GEMINI_2_FLASH_LITE = "gemini/gemini-2.0-flash-lite";
    public static final String GEMINI_15_PRO       = "gemini/gemini-1.5-pro";
    public static final String GEMINI_15_FLASH     = "gemini/gemini-1.5-flash";
    
    private static final Map<String, ModelInfo> MODELS = new HashMap<>();
    
    static {
        // GPT-4o (2024-11-20 snapshot)
        MODELS.put(GPT_4O, new ModelInfo("openai", GPT_4O, 128_000));
        
        // GPT-4o-mini
        MODELS.put(GPT_4O_MINI, new ModelInfo("openai", GPT_4O_MINI, 128_000));

        // GPT-4.1 nano
        MODELS.put(GPT_41_NANO, new ModelInfo("openai", GPT_41_NANO, 1_047_576));
        
        // GPT-4-turbo
        MODELS.put(GPT_4_TURBO, new ModelInfo("openai", GPT_4_TURBO,128_000));
        
        // GPT-4 (older)
        MODELS.put(GPT_4, new ModelInfo("openai", GPT_4, 8_192));
        
        // GPT-3.5-turbo
        MODELS.put(GPT_35_TURBO, new ModelInfo("openai", GPT_35_TURBO, 16_385));
        
        MODELS.put(GEMINI_2_FLASH,      new ModelInfo("google", GEMINI_2_FLASH,       1_048_576));
        MODELS.put(GEMINI_FLASH_LATEST, new ModelInfo("google", GEMINI_FLASH_LATEST, 1_048_576));
        MODELS.put(GEMINI_2_FLASH_LITE, new ModelInfo("google", GEMINI_2_FLASH_LITE,  1_048_576));
        MODELS.put(GEMINI_15_PRO,       new ModelInfo("google", GEMINI_15_PRO,        2_097_152));
        MODELS.put(GEMINI_15_FLASH,     new ModelInfo("google", GEMINI_15_FLASH,      1_048_576));
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