package io.oxyjen.llm;

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

	@Override
	public String process(String input, NodeContext context) {
		// TODO Auto-generated method stub
		return null;
	}
    
    
}
