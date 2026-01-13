package io.oxyjen.core;

import java.time.Instant;
import java.util.List;

/**
 * Generic memory interface for storing state and history.
 * 
 * This is domain-agnostic i.e. it knows nothing about LLMs,
 * messages, roles, or AI concepts. Those belong in higher layers.
 * 
 * Two modes:
 * 1. Key-value store (arbitrary data)
 * 2. Ordered history (append-only log)
 */
/**
 * Memory is execution-scoped shared state.
 * It contains NO domain logic and NO intelligence.
 */
public interface Memory {
    
    /**
     * Store a value by key.
     */
    void put(String key, Object value);
    
    /**
     * Retrieve a value by key.
     * Returns null if key doesn't exist.
     */
    <T> T get(String key);
    
    /**
     * Retrieve a value by key with type checking.
     * Throws ClassCastException if type doesn't match.
     */
    <T> T get(String key, Class<T> type);
    
    /**
     * Check if key exists.
     */
    boolean contains(String key);
    
    /**
     * Remove a key.
     */
    void remove(String key);
    
    /**
     * Clear all key-value data.
     */
    void clear();
    
    /**
     * Append an entry to ordered history.
     * 
     * @param type Entry type (e.g., "chat", "log", "event")
     * @param value Entry value (any object)
     */
    void append(String type, Object value);
    
    /**
     * Get all history entries in order.
     * Returns immutable list.
     */
    List<MemoryEntry> entries();
    
    /**
     * Get recent N entries.
     */
    List<MemoryEntry> recent(int n);
    
    /**
     * Get entries by type.
     */
    List<MemoryEntry> byType(String type);
    
    /**
     * Clear all history.
     */
    void clearHistory();
    
    /**
     * Entry in ordered history.
     * 
     * @param type Entry type (generic string)
     * @param value Entry value (any object)
     * @param timestamp When entry was created
     */
    record MemoryEntry(
        String type,
        Object value,
        Instant timestamp
    ) {}
}