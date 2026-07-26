package io.oxyjen.persist;

import java.util.List;
import java.util.OptionalLong;

import io.oxyjen.execution.ExecutionRecord;

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
        OptionalLong totalCount,
        int offset,
        int limit
) {

    public ExecutionPage {
        records = List.copyOf(records);
        if (totalCount == null) throw new IllegalArgumentException("totalCount must not be null");
    }

    /** Whether there are more records beyond this page. */
    public boolean hasMore() {
        if (totalCount.isPresent()) {
        	return (long) offset + records.size() < totalCount.getAsLong();
        }
        return records.size() == limit; // best-effort for backends that can't count
    }

    /** The offset to pass to the next page query. */
    public int nextOffset() {
        return offset + records.size();
    }

    /** Whether this page is empty. */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /**
     * Empty page where the total count is known to be zero.
     * Use when the backend can confirm no records match.
     */
    public static ExecutionPage emptyKnown(int offset, int limit) {
        return new ExecutionPage(List.of(), OptionalLong.of(0), offset, limit);
    }
 
    /**
     * Empty page where the total count is unknown.
     * Use when the backend (e.g. S3) cannot compute the total efficiently.
     */
    public static ExecutionPage emptyUnknown(int offset, int limit) {
        return new ExecutionPage(List.of(), OptionalLong.empty(), offset, limit);
    }
 
    /**
     * Page with records and a known total count.
     */
    public static ExecutionPage of(List<ExecutionRecord> records, long totalCount, int offset, int limit) {
        return new ExecutionPage(records, OptionalLong.of(totalCount), offset, limit);
    }
 
    /**
     * Page with records but unknown total count.
     * Use when the backend returns results but can't compute the total.
     */
    public static ExecutionPage ofUnknownTotal(List<ExecutionRecord> records,int offset, int limit) {
        return new ExecutionPage(records, OptionalLong.empty(), offset, limit);
    }
}