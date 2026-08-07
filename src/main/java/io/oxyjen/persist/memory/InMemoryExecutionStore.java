package io.oxyjen.persist.memory;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private final ConcurrentMap<String, ExecutionRecord> store;
    
    /** Creates an empty in-memory store */
    public InMemoryExecutionStore() {
    	this.store = new ConcurrentHashMap<>();
    }
    
    /**
     * Creates an in-memory store pre-populated with the supplied records.
     * Useful for replay, importing snapshots, migrations, and tests.
     *
     * @param records initial records to populate
     * @throws ExecutionAlreadyExistsException if duplicate execution ids exist
     */
    public InMemoryExecutionStore(Collection<ExecutionRecord> records) {
        Objects.requireNonNull(records, "records must not be null");
        this.store = new ConcurrentHashMap<>();
        for (ExecutionRecord record : records) {
            ExecutionRecord existing = store.putIfAbsent(record.executionId(), record);
            if (existing != null) {
                throw new ExecutionAlreadyExistsException(record.executionId());
            }
        }
    }

    @Override
    public void save(ExecutionRecord record) {
    	Objects.requireNonNull(record, "record must not be null");
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
    
    @Override
    public ExecutionPage find(ExecutionQuery query) {
    	Objects.requireNonNull(query, "query must not be null");
 
        Stream<ExecutionRecord> stream = filtered(store.values().stream(), query);
 
        // sorting + count before pagination 
        List<ExecutionRecord> allMatching = stream.sorted(comparatorFor(query.sortField(), query.sortDirection())).toList();
        long totalCount = allMatching.size();
 
        // offset + limit 
        List<ExecutionRecord> page = allMatching.stream()
                .skip(query.offset())
                .limit(query.limit())
                .toList();
 
        return ExecutionPage.of(page, totalCount, query.offset(), query.limit());
    }
    
    @Override
    public long count(ExecutionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return filtered(store.values().stream(), query).count();
    }

    /** Returns the number of records currently held in memory. */
    public int size() {
        return store.size();
    }

    /** Removes all records, useful for test teardown. */
    public void clear() {
        store.clear();
    }
    
    /** Applies all query filters to a stream. Shared by find() and count(). */
    private Stream<ExecutionRecord> filtered(Stream<ExecutionRecord> stream,
                                              ExecutionQuery query) {
        if (query.workflowId().isPresent()) {
            String wid = query.workflowId().get();
            // workflowId is a top-level field, no event scanning needed
            stream = stream.filter(r -> wid.equals(r.workflowId()));
        }
        if (query.status().isPresent()) {
            ExecutionStatus s = query.status().get();
            stream = stream.filter(r -> r.status() == s);
        }
        
        /*
         * Time filters are exclusive:
         *
         * startedAfter(t)  -> startedAt > t
         * startedBefore(t) -> startedAt < t
         * finishedAfter(t) -> finishedAt > t
         * finishedBefore(t)-> finishedAt < t
         */
        if (query.startedAfter().isPresent()) {
            Instant t = query.startedAfter().get();
            // exclusive (>), documented on ExecutionQuery
            stream = stream.filter(r -> r.startedAt() != null && r.startedAt().isAfter(t));
        }
        if (query.startedBefore().isPresent()) {
            Instant t = query.startedBefore().get();
            // exclusive (<)
            stream = stream.filter(r -> r.startedAt() != null && r.startedAt().isBefore(t));
        }
        if (query.finishedAfter().isPresent()) {
            Instant t = query.finishedAfter().get();
            stream = stream.filter(r -> r.finishedAt() != null && r.finishedAt().isAfter(t));
        }
        if (query.finishedBefore().isPresent()) {
            Instant t = query.finishedBefore().get();
            stream = stream.filter(r -> r.finishedAt() != null && r.finishedAt().isBefore(t));
        }
        return stream;
    }
    
    /** Builds a comparator for the given sor field and direction*/
    private Comparator<ExecutionRecord> comparatorFor(SortField field, SortDirection direction) {

        Comparator<ExecutionRecord> base = switch (field) {
        	case STARTED_AT -> Comparator.comparing(ExecutionRecord::startedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

        	case FINISHED_AT -> Comparator.comparing(ExecutionRecord::finishedAt, Comparator.nullsFirst(Comparator.naturalOrder()));

        	case STATUS -> Comparator.comparing(ExecutionRecord::status);

        	case WORKFLOW_ID -> Comparator.comparing(ExecutionRecord::workflowId);

        	case EXECUTION_ID -> Comparator.comparing(ExecutionRecord::executionId);
        };

        return direction == SortDirection.DESC ? base.reversed() : base;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be blank");
    }
}