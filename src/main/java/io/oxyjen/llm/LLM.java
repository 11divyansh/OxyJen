package io.oxyjen.llm;
/**
 * Public entry point for Oxyjen LLM system.
 * 
 * This is a facade + factory. Users never touch:
 * - OpenAIClient
 * - HTTP clients
 * - Provider details
 * 
 * They only touch LLM.
 * 
 * Responsibilities (v0.2 only):
 * - Route model name → provider
 * - Apply defaults (env vars, retries)
 * - Return a ChatModel
 * - Expose chain() and profile() entry points
 * 
 * It must NOT:
 * - Be smart
 * - Do config loading
 * - Do runtime routing
 */
public final class LLM {
    
    private LLM() {
        // No instances - static factory only
    }
    
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
            return OpenAIChatModel.create(model);
        }
        
        // v0.3: Add more providers
        // if (normalized.startsWith("claude")) {
        //     return AnthropicModels.create(model);
        // }
        
        throw new IllegalArgumentException(
            "Unknown model: " + model + "\n" +
            "Supported in v0.2: gpt-4o, gpt-4o-mini, gpt-3.5-turbo\n" +
            "Coming in v0.3: claude-*, gemini-*"
        );
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
}