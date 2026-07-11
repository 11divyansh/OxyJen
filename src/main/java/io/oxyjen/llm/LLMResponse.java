package io.oxyjen.llm;

import java.math.BigDecimal;

import io.oxyjen.llm.models.ModelInfo;

/**
 * The result of a single {@link ChatModel#chat(String)} call.
 *
 * <p>Replaces the previous {@code String} return type. Every field beyond
 * {@link #text()} is optional providers that don't report token usage or
 * cost leave those fields {@code null} or {@code 0}. Callers that only need
 * the text can call {@link #text()} and ignore the rest; callers that build
 * {@link io.oxyjen.execution.NodeMetrics.LlmNodeMetrics} use all fields.
 *
 * @param text              the model's response text; never {@code null}
 * @param promptTokens      input tokens consumed, or {@code null} if the
 *                          provider doesn't report this
 * @param completionTokens  output tokens produced, or {@code null} if the
 *                          provider doesn't report this
 * @param costMicros        cost in micros of a US dollar (1 USD = 1,000,000
 *                          micros); {@code 0} if cost is unknown or not
 *                          tracked for this provider
 * @param provider          name of the provider that produced this response
 *                          (e.g. "openai", "gemini", "ollama")
 * @param model             specific model identifier used
 *                          (e.g. "gpt-4o", "gemini-1.5-pro")
 * @param cacheHit          whether this response was served from
 *                          provider-side prompt cache; {@code null} if
 *                          the provider doesn't report caching
 */
public record LLMResponse(
        String text,
        Long promptTokens,
        Long completionTokens,
        long costMicros,
        ModelInfo modelInfo,
        Boolean cacheHit
) {

    public LLMResponse {
        if (text == null) throw new IllegalArgumentException("LLMResponse.text must not be null");
    }

    /**
     * Convenience constructor for providers that don't report any metrics —
     * just wrap the response text.
     */
    public static LLMResponse of(String text) {
        return new LLMResponse(text, null, null, 0L, null, null);
    }

    /**
     * Total tokens (prompt + completion), or {@code null} if neither
     * field is present.
     */
    public Long totalTokens() {
        if (promptTokens == null && completionTokens == null) return null;
        return (promptTokens == null ? 0L : promptTokens)
             + (completionTokens == null ? 0L : completionTokens);
    }

    /**
     * Cost as a {@link BigDecimal} in US dollars, for display or invoicing.
     * Returns {@link BigDecimal#ZERO} if {@link #costMicros()} is 0.
     */
    public BigDecimal costUsd() {
        return BigDecimal.valueOf(costMicros, 6);
    }
}