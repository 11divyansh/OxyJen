package io.oxyjen.llm;

/**
 * The smallest, stable contract for "something that can chat."
 * 
 * This is the ROOT abstraction. Everything else exists to produce a ChatModel.
 * 
 * 
 * <p><b>Migration from v0.5:</b> implementations that previously returned
 * {@code String} should wrap their response in {@link LLMResponse#of(String)}
 * as a minimal migration, then add token/cost fields as the provider API
 * exposes them.
 */
public interface ChatModel {
    
    /**
     * Execute a single chat completion.
     * 
     * Implementations decide how input is interpreted:
     * - Could be a single message
     * - Could be a full conversation
     * - Could be a prompt template
     * 
     * @param input The input string
     * @return The model's response
     */
    LLMResponse chat(String input);
}