package io.oxyjen.llm;

import java.util.concurrent.Semaphore;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;

/**
 * LLM as a first-class graph node.
 * 
 * Features:
 * - Wraps any ChatModel
 * - Participates fully in graph execution
 * - Uses Memory for conversation history
 * - Logs costs and tokens
 * - Handles errors
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
public final class LLMNode implements NodePlugin<String, String>, UsesRuntimeLimiter {
    
    private final ChatModel model;
    private final String memoryName;
    
    private LLMNode(ChatModel model, String memoryName) {
        this.model = model;
        this.memoryName = memoryName;
    }
    
    @Override
    public String process(String input, NodeContext context) {
        Semaphore runtimeLimiter = acquireRuntimeLimiterIfNested(context);
        try {
            Memory memory = context.memory(memoryName);
            
            // 1. Store user input
            memory.append("user", input);
            
            // 2. Call model
            String response = model.chat(input);
            
            // 3. Store assistant response
            memory.append("assistant", response);
            
            return response;
        } finally {
            releaseRuntimeLimiter(runtimeLimiter);
        }
    }
    
    @Override
    public String getName() {
        return "LLM[" + model.getClass().getSimpleName() + "]";
    }
   
    
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

    private Semaphore acquireRuntimeLimiterIfNested(NodeContext context) {
        if (context == null || !context.isChild()) {
            return null;
        }
        var runtime = context.getRuntime();
        if (runtime == null) {
            return null;
        }
        Semaphore limiter = runtime.getLimiter();
        try {
            limiter.acquire();
            return limiter;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for runtime limiter", e);
        }
    }

    private void releaseRuntimeLimiter(Semaphore limiter) {
        if (limiter != null) {
            limiter.release();
        }
    }
}
