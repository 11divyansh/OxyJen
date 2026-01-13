package io.oxyjen.llm.models;

import java.util.List;

/**
 * Request to OpenAI chat completions API.
 */
public record ChatRequest(
	String model,
	List<Message> messages,
	Double temperature,
	Integer maxTokens
) {
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		private String model;
		private List<Message> messages = new java.util.ArrayList<>();
		private Double temperature;
		private Integer maxTokens;
		
		public Builder model(String model) {
			this.model = model;
			return this;
		}
	
	
		public Builder addMessage(String role, String content) {
			this.messages.add(new Message(role, content));
			return this;
		}
	
		public Builder temperature(double temp) {
			this.temperature = temp;
			return this;
		}
	
		public Builder maxTokens(int tokens) {
			this.maxTokens = tokens;
			return this;
		}
	
		public ChatRequest build() {
			if(model == null) {
				throw new IllegalStateException("Model must be set");
			}
			if(messages.isEmpty()) {
				throw new IllegalStateException("At least one message required");
			}
			return new ChatRequest(model, messages, temperature, maxTokens);
		}
	}
}
