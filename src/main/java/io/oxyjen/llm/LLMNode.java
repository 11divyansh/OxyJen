package io.oxyjen.llm;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;

import io.oxyjen.core.Memory;
import io.oxyjen.core.NodeContext;
import io.oxyjen.core.NodePlugin;
import io.oxyjen.execution.ExecutionEvent;
import io.oxyjen.execution.FailureInfo;
import io.oxyjen.execution.metrics.NodeMetrics;
import io.oxyjen.observe.ObservationBus;

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
        String executionId = context.getMetadata("executionId");
        ObservationBus bus = context.getRuntime() != null
                ? context.getRuntime().observationBus()
                : null;
 
        Instant start = Instant.now();
        int attempt = resolveAttempt(context);
        try {
            Memory memory = context.memory(memoryName);
            
            // 1. Store user input
            memory.append("user", input);
            
            // 2. Call model
            LLMResponse response = model.chat(input);
            
            // 3. Store assistant response
            memory.append("assistant", response);
            
            Duration duration = Duration.between(start, Instant.now());
            
            // emit NodeCompleted with LlmNodeMetrics
            // This overrides the BasicNodeMetrics the executor emits for generic
            // nodes. LLMNode is the only node that knows provider/token/cost info,
            // so it owns emitting the richer metrics variant.
            if (bus != null && !bus.isEmpty() && executionId != null) {
                bus.emit(new ExecutionEvent.NodeCompleted(
                        executionId,
                        Instant.now(),
                        getName(),
                        new NodeMetrics.LlmNodeMetrics(
                                duration,
                                response.promptTokens(),
                                response.completionTokens(),
                                response.costMicros(),
                                response.modelInfo(),
                                null,       // outputValid - set by SchemaNode, not here
                                null        // toolCalls - set by ToolNode when tool use is added
                        )
                ));
            }
            return response.text();
        } catch(Exception e) {
        	// emit NodeFailed
            if (bus != null && !bus.isEmpty() && executionId != null) {
                bus.emit(new ExecutionEvent.NodeFailed(
                        executionId,
                        Instant.now(),
                        getName(),
                        FailureInfo.from(e),
                        attempt
                ));
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
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
    
    /**
     * Reads the current attempt number from context metadata, defaulting to 1.
     * RetryNode increments this before each attempt so the emitted events
     * carry the correct attempt number.
     */
    private int resolveAttempt(NodeContext context) {
        Object attempt = context.getMetadata("attempt:" + getName());
        return attempt instanceof Integer i ? i : 1;
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