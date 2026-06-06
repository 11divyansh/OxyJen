package io.oxyjen.llm;

/**
 * The smallest, stable contract for "something that can chat."
 * 
 * This is the ROOT abstraction. Everything else exists to produce a ChatModel.
 * 
 * Just: input string → output string
 * Keeping it boring
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
    String chat(String input);
}