package io.oxyjen.llm.transport.gemini;

import java.time.Duration;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;
import io.oxyjen.resilience.ratelimit.RateLimitedChatModel;
import io.oxyjen.resilience.ratelimit.RateLimiter;
import io.oxyjen.resilience.ratelimit.RateLimiters;

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
    
    /**
     * No rate limiter by default.
     * Free tier users should call withRateLimit(4) or withRateLimiter(RateLimiters.geminiFreeTier())
     * LLMChain handles occasional 429s via retry + backoff as fallback.
     */
    private RateLimiter rateLimiter = null; //opt-in, not default
    private volatile ChatModel rateLimitedDelegate;
    
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
    
    // copy constructor, creates GeminiChatModel without rateLimit
    // so RateLimitedChatModel wraps a clean delegate
    private GeminiChatModel(GeminiChatModel source) {
        this.client = source.client;
        this.model = source.model;
        this.temperature = source.temperature;
        this.maxTokens = source.maxTokens;
    }

    @Override
    public String chat(String input) {
    	if (rateLimiter != null) {
            if (rateLimitedDelegate == null) {
                synchronized(this) {
                    if (rateLimitedDelegate == null) {
                        rateLimitedDelegate = RateLimitedChatModel.of(new GeminiChatModel(this), rateLimiter);
                    }
                }
            }
            return rateLimitedDelegate.chat(input);
        }
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
        this.rateLimitedDelegate = null;
        return this;
    }

    /**
     * Set max tokens in response.
     */
    public GeminiChatModel withMaxTokens(int tokens) {
        this.maxTokens = tokens;
        this.rateLimitedDelegate = null;
        return this;
    }
    
    public GeminiChatModel withRateLimit(int requestsPerMinute) {
        return withRateLimiter(RateLimiters.fixedInterval(requestsPerMinute));
    }
    
    /**
     * Set rate limiter with full control over algorithm.
     *
     * Examples:
     * .withRateLimiter(RateLimiters.fixedInterval(12))
     * .withRateLimiter(RateLimiters.tokenBucket(12))
     * .withRateLimiter(RateLimiters.geminiFreeTier())
     * .withRateLimiter(
     *     RateLimiter.builder()
     *         .requestsPerMinute(12)
     *         .algorithm(Algorithm.SLIDING_WINDOW)
     *         .build()
     * )
     */
    public GeminiChatModel withRateLimiter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.rateLimitedDelegate = null;
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