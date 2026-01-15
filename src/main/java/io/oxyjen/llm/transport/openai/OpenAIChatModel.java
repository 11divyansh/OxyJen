package io.oxyjen.llm.transport.openai;

import io.oxyjen.llm.ChatModel;
import io.oxyjen.llm.models.ChatRequest;
import io.oxyjen.llm.models.ChatResponse;
import io.oxyjen.llm.models.TokenUsage;

/**
 * OpenAI implementation of ChatModel.
 * 
 * This wraps OpenAIClient and implements the simple ChatModel interface.
 * It translates the simple chat(String) call into OpenAI's message format.
 * 
 * Responsibilities:
 * - Implement ChatModel.chat()
 * - Build OpenAI request objects
 * - Delegate to OpenAIClient for HTTP
 * - Return clean string responses
 * 
 * What it does NOT do:
 * - HTTP logic (delegates to OpenAIClient)
 * - Memory management (that's NodeContext)
 * - Graph execution (that's Executor)
 */
public final class OpenAIChatModel implements ChatModel {

	@Override
	public String chat(String input) {
		// TODO Auto-generated method stub
		return null;
	}
    
}