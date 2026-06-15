package io.oxyjen.llm;
import java.time.Duration;

import io.oxyjen.llm.transport.gemini.GeminiModels;
import io.oxyjen.llm.transport.openai.OpenAIModels;
import io.oxyjen.resilience.ratelimit.RateLimitedChatModel;
import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;
/**
 * Public entry point for Oxyjen LLM system.
 * 
 * This is a facade + factory.
 * 
 * Responsibilities (v0.2 only):
 * - Route model name → provider
 * - Apply defaults (env vars, retries)
 * - Return a ChatModel
 * - Expose chain() and profile() entry points
 * 
 */
public final class LLM {
    
    private LLM() {}
    
    /**
     * Create a ChatModel for a given model name.
     * 
     * Auto-detects provider from model name:
     * - "gpt-*" → OpenAI
     * - "claude-*" → Anthropic (v0.3)
     * - "gemini-*" → Google (v0.3)
     * 
     * Examples:
     * <pre>
     * ChatModel model = LLM.of("gpt-4o");
     * String response = model.chat("Hello");
     * </pre>
     * 
     * @param model Model name (e.g., "gpt-4o", "gpt-4o-mini")
     * @return ChatModel instance
     * @throws IllegalArgumentException if model unknown
     */
    public static ChatModel of(String model) {
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }
        
        String normalized = model.trim().toLowerCase();
        
        // v0.2: Simple routing for OpenAI only
        if (normalized.startsWith("gpt")) {
            return OpenAIModels.create(model);
        }
        
        if (normalized.startsWith("gemini/")) {
            return GeminiModels.create(model);
        }
        
        throw new IllegalArgumentException(
            "Unknown model: " + model + "\n" +
            "Supported in v0.2: gpt-4o, gpt-4o-mini, gpt-3.5-turbo\n" +
            "Coming in future versions: claude-*, ollama"
        );
    }
    
    /**
     * Create OpenAI model with explicit API key.
     */
    public static ChatModel openai(String model, String apiKey) {
        return OpenAIModels.create(model, apiKey);
    }
    
    /**
     * Entry point for building fallback chains.
     * 
     * Examples:
     * <pre>
     * ChatModel resilient = LLM.chain()
     *     .primary("gpt-4o")
     *     .fallback("gpt-3.5-turbo")
     *     .retry(3)
     *     .build();
     * </pre>
     * 
     * @return LLMChain builder
     */
    public static LLMChain.Builder chain() {
        return LLMChain.builder();
    }
    
    /**
     * Wrap any ChatModel with rate limiting.
     *
     * Examples:
     * ChatModel model = LLM.withRateLimit(
     *     LLM.gemini(modelName, apiKey),
     *     RateLimiters.geminiFreeTier()
     * );
     *
     * ChatModel model = LLM.withRateLimit(
     *     LLM.gemini(modelName, apiKey),
     *     RateLimiter.builder()
     *         .requestsPerMinute(12)
     *         .algorithm(Algorithm.TOKEN_BUCKET)
     *         .build()
     * );
     */
    public static ChatModel withRateLimit(ChatModel model, RateLimiter rateLimiter) {
        return RateLimitedChatModel.of(model, rateLimiter);
    }

    /**
     * Shortcut - fixed interval rate limit.
     */
    public static ChatModel withRateLimit(ChatModel model, int requestsPerMinute) {
        return RateLimitedChatModel.of(
            model, 
            RateLimiters.fixedInterval(requestsPerMinute)
        );
    }
    
    /**
     * Profile-based lookup.
     * 
     * Profiles map use-case names to models:
     * - "fast" → gpt-4o-mini
     * - "smart" → gpt-4o
     * - "cheap" → gpt-3.5-turbo
     * 
     * Examples:
     * <pre>
     * ChatModel model = LLM.profile("fast");
     * String response = model.chat("Quick task");
     * </pre>
     * 
     * @param name Profile name
     * @return ChatModel for that profile
     * @throws IllegalArgumentException if profile unknown
     */
    public static ChatModel profile(String name) {
        return LLMProfiles.get(name);
    }
    
    /**
     * Shortcut for GPT-4o (most common).
     */
    public static ChatModel gpt4o() {
        return of("gpt-4o");
    }
    
    /**
     * Shortcut for GPT-4o-mini (fast & cheap).
     */
    public static ChatModel gpt4oMini() {
        return of("gpt-4o-mini");
    }
    
    public static ChatModel gemini(String model) {
        return GeminiModels.create(model);
    }

    public static ChatModel gemini(String model, String apiKey) {
        return GeminiModels.create(model, apiKey);
    }

    public static ChatModel gemini(String model, String apiKey, Duration requestTimeout) {
        return GeminiModels.create(model, apiKey, requestTimeout);
    }
    
    
    // TODO v0.4+: expose profile registration via LLM
}