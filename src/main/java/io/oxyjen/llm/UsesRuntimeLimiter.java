package io.oxyjen.llm;

/**
 * Marker interface for NodePlugin implementations that make
 * external IO calls and should acquire the ExecutionRuntime
 * concurrency limiter before executing.
 *
 * LLMNode and SchemaNode implement this by default.
 * Custom nodes that call LLMs or external APIs should implement
 * this so ParallelExecutor applies proper concurrency control.
 *
 * Example:
 * <pre>
 * public class MyCustomLLMNode implements NodePlugin<String, String>,
 *                                          UsesRuntimeLimiter {
 *     // ParallelExecutor will acquire limiter before calling process()
 * }
 * </pre>
 */
public interface UsesRuntimeLimiter {}