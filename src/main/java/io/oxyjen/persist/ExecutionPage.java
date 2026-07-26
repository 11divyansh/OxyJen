package io.oxyjen.persist;

import io.oxyjen.execution.ExecutionRecord;

import java.util.List;

/**
 * A single page of {@link ExecutionRecord}s returned by
 * {@link ExecutionStore#find(ExecutionQuery)}.
 *
 * <p>Never returns an unbounded {@code List<ExecutionRecord>} on a store
 * with millions of executions that would load everything into memory.
 *
 * @param records     the records on this page; never {@code null}, may be empty
 * @param totalCount  total number of records matching the query across all pages,
 *                    or {@code -1} if the backend cannot compute this efficiently
 *                    (e.g. S3, some NoSQL stores)
 * @param offset      the offset this page starts at
 * @param limit       the limit that was applied
 */
public record ExecutionPage(
        List<ExecutionRecord> records,
        long totalCount,
        int offset,
        int limit
) {

    public ExecutionPage {
        records = List.copyOf(records);
    }

    /** Whether there are more records beyond this page. */
    public boolean hasMore() {
        if (totalCount < 0) return records.size() == limit; // best-effort
        return (long) offset + records.size() < totalCount;
    }

    /** The offset to pass to the next page query. */
    public int nextOffset() {
        return offset + records.size();
    }

    /** Whether this page is empty. */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** Convenience factory for an empty page. */
    public static ExecutionPage empty(int offset, int limit) {
        return new ExecutionPage(List.of(), 0, offset, limit);
    }
}