package io.oxyjen.core;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of Memory interface.
 * 
 * Thread-safe, simple, fast.
 * Used by default, can be replaced with Redis, database, etc.
 */
public final class InMemoryMemory implements Memory {
    
    private final String scope;
    private final Map<String, Object> store = new ConcurrentHashMap<>();
    private final List<MemoryEntry> history = new CopyOnWriteArrayList<>();
    
    public InMemoryMemory(String scope) {
        this.scope = Objects.requireNonNull(scope);
    }
    
    @Override
    public void put(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");
        store.put(key, value);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) store.get(key);
    }
    
    @Override
    public <T> T get(String key, Class<T> type) {
        Object value = store.get(key);
        if (value == null) return null;
        
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                "Expected " + type.getName() + " but got " + value.getClass().getName()
            );
        }
        
        return type.cast(value);
    }
    
    @Override
    public boolean contains(String key) {
        return store.containsKey(key);
    }
    
    @Override
    public void remove(String key) {
        store.remove(key);
    }
    
    @Override
    public void clear() {
        store.clear();
    }
    
    @Override
    public void append(String type, Object value) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        
        history.add(new MemoryEntry(type, value, Instant.now()));
    }
    
    @Override
    public List<MemoryEntry> entries() {
        return Collections.unmodifiableList(history);
    }
    
    @Override
    public List<MemoryEntry> recent(int n) {
        int size = history.size();
        int start = Math.max(0, size - n);
        return history.subList(start, size);
    }
    
    @Override
    public List<MemoryEntry> byType(String type) {
        return history.stream()
            .filter(e -> e.type().equals(type))
            .collect(Collectors.toUnmodifiableList());
    }
    
    @Override
    public void clearHistory() {
        history.clear();
    }
    
    public String getScope() {
        return scope;
    }
}