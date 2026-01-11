package io.oxyjen.llm;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * LLM as a first-class graph node.
 * 
 * This is where Oxyjen beats LangChain4j.
 * 
 * Features:
 * - Wraps any ChatModel
 * - Participates fully in graph execution
 * - Uses Memory for conversation history
 * - Logs costs and tokens (v0.3)
 * - Handles errors gracefully
 * 
 * This is Layer 3 (Execution Control).
 * 
 * Example:
 * <pre>
 * Graph pipeline = GraphBuilder.named("chatbot")
 *     .addNode(LLMNode.builder()
 *         .model("gpt-4o")
 *         .memory("conversation")
 *         .build())
 *     .build();
 * </pre>
 */
public final class LLMNode implements NodePlugin<String, String> {
    
    private final ChatModel model;
    private final String memoryName;
    
    private LLMNode(ChatModel model, String memoryName) {
        this.model = model;
        this.memoryName = memoryName;
    }
    
    @Override
    public String process(String input, NodeContext context) {
        Memory memory = context.memory(memoryName);
        
        // 1. Store user input
        memory.append("user", input);
        
        // 2. Call model
        String response = model.chat(input);
        
        // 3. Store assistant response
        memory.append("assistant", response);
        
        return response;
    }
    
    @Override
    public String getName() {
        return "LLM[" + model.getClass().getSimpleName() + "]";
    }
    
    // ========== Builder ==========
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        
        private ChatModel model;
        private String memoryName = "chat";
        
        /**
         * Set model by name.
         */
        public Builder model(String modelName) {
            this.model = LLM.of(modelName);
            return this;
        }
        
        /**
         * Set model directly (allows LLMChain).
         */
        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }
        
        /**
         * Use a profile.
         */
        public Builder profile(String profileName) {
            this.model = LLM.profile(profileName);
            return this;
        }
        
        /**
         * Use a chain (fallbacks).
         */
        public Builder chain(LLMChain chain) {
            this.model = chain;
            return this;
        }
        
        /**
         * Set memory scope name.
         */
        public Builder memory(String name) {
            this.memoryName = name;
            return this;
        }
        
        public LLMNode build() {
            if (model == null) {
                throw new IllegalStateException("ChatModel must be provided");
            }
            return new LLMNode(model, memoryName);
        }
    }
}
