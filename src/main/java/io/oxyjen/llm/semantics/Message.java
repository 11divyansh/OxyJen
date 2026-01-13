package io.oxyjen.llm.semantics;

/**
 * Message in chat request.
 */
// role will be enum based in v0.3
public record Message(String role, String content) {}
