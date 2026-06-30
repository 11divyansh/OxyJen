package io.oxyjen.llm.models;

/**
 * Model metadata.
 */
public record ModelInfo(String provider, String model, int contextLength) {}