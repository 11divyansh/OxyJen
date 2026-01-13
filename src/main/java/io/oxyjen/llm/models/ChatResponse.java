package io.oxyjen.llm.models;

/**
* Response from OpenAI chat completions API.
*/
public record ChatResponse(String content, TokenUsage usage) {}
