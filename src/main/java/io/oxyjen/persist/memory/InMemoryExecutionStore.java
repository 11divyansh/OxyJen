package io.oxyjen.persist.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.oxyjen.execution.ExecutionRecord;
import io.oxyjen.execution.ExecutionStatus;
import io.oxyjen.persist.ExecutionAlreadyExistsException;
import io.oxyjen.persist.ExecutionPage;
import io.oxyjen.persist.ExecutionQuery;
import io.oxyjen.persist.ExecutionQuery.SortDirection;
import io.oxyjen.persist.ExecutionQuery.SortField;
import io.oxyjen.persist.ExecutionStore;

/**
 * Thread-safe, zero-dependency {@link ExecutionStore} backed by a
 * {@link ConcurrentHashMap}.
 *
 * <p>Intended for:
 * <ul>
 *   <li>Unit and integration tests — no database setup required</li>
 *   <li>Short-lived runs where durability across restarts is not needed</li>
 *   <li>Development and local experimentation</li>
 * </ul>
 *
 * <p>All data is lost when the process exits. For durability, use
 * {@code SqliteExecutionStore} (embedded, zero infra) or
 * {@code JdbcExecutionStore} (Postgres, MySQL).
 *
 * <p>Filtering and sorting in {@link #find} is done in Java — linear scan
 * over all stored records. Fine for development; use a real backend for
 * large volumes.
 */
public final class InMemoryExecutionStore implements ExecutionStore {

    private final Map<String, ExecutionRecord> store = new ConcurrentHashMap<>();

    @Override
    public void save(ExecutionRecord record) {
        if (record == null) throw new IllegalArgumentException("record must not be null");
        ExecutionRecord existing = store.putIfAbsent(record.executionId(), record);
        if (existing != null) {
            throw new ExecutionAlreadyExistsException(record.executionId());
        }
    }

    @Override
    public Optional<ExecutionRecord> load(String executionId) {
        requireNonBlank(executionId, "executionId");
        return Optional.ofNullable(store.get(executionId));
    }

    @Override
    public boolean exists(String executionId) {
        requireNonBlank(executionId, "executionId");
        return store.containsKey(executionId);
    }

    @Override
    public void delete(String executionId) {
        requireNonBlank(executionId, "executionId");
        store.remove(executionId);
    }

    /** Returns the number of records currently held in memory. */
    public int size() {
        return store.size();
    }

    /** Removes all records, useful for test teardown. */
    public void clear() {
        store.clear();
    }

    private Comparator<ExecutionRecord> buildComparator(
            SortField field, SortDirection direction) {

        Comparator<ExecutionRecord> base = switch (field) {
            case STARTED_AT -> Comparator.comparing(r -> r.startedAt() == null ? Instant.EPOCH : r.startedAt());
            case FINISHED_AT -> Comparator.comparing(r -> r.finishedAt() == null ? Instant.EPOCH : r.finishedAt());
        };

        return direction == SortDirection.DESC ? base.reversed() : base;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
    }
}