package io.oxyjen.persist;

import io.oxyjen.execution.ExecutionStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Type-safe query object for filtering {@link io.oxyjen.execution.ExecutionRecord}s
 * from an {@link ExecutionStore}.
 *
 * <p>Two equivalent styles are supported:
 *
 * <pre>{@code
 * // Builder style
 * ExecutionQuery query = ExecutionQuery.builder()
 *         .workflowId("summarizer")
 *         .status(ExecutionStatus.COMPLETED)
 *         .startedAfter(Instant.now().minus(1, ChronoUnit.DAYS))
 *         .orderBy(SortField.STARTED_AT, SortDirection.DESC)
 *         .limit(100)
 *         .build();
 *
 * // Lambda style (via ExecutionStore.find)
 * store.find(q -> q.workflowId("summarizer")
 *                   .status(ExecutionStatus.COMPLETED)
 *                   .orderBy(SortField.STARTED_AT, SortDirection.DESC)
 *                   .limit(100));
 * }</pre>
 *
 * <p>All filter fields are optional — an empty query matches all records.
 * Sorting defaults to {@link SortField#STARTED_AT} {@link SortDirection#DESC}
 * (newest first) so results are never in undefined order.
 */
public final class ExecutionQuery {

    /** Fields that results can be sorted by. */
    public enum SortField {
        STARTED_AT,
        FINISHED_AT
    }

    /** Sort direction. */
    public enum SortDirection {
        ASC,
        DESC
    }

    private final String workflowId;
    private final ExecutionStatus status;
    private final Instant startedAfter;
    private final Instant startedBefore;
    private final Instant finishedAfter;
    private final Instant finishedBefore;
    private final SortField sortField;
    private final SortDirection sortDirection;
    private final int limit;
    private final int offset;

    private ExecutionQuery(Builder builder) {
        this.workflowId     = builder.workflowId;
        this.status         = builder.status;
        this.startedAfter   = builder.startedAfter;
        this.startedBefore  = builder.startedBefore;
        this.finishedAfter  = builder.finishedAfter;
        this.finishedBefore = builder.finishedBefore;
        this.sortField      = builder.sortField;
        this.sortDirection  = builder.sortDirection;
        this.limit          = builder.limit;
        this.offset         = builder.offset;
    }

    public Optional<String> workflowId()      { return Optional.ofNullable(workflowId); }
    public Optional<ExecutionStatus> status() { return Optional.ofNullable(status); }
    public Optional<Instant> startedAfter()   { return Optional.ofNullable(startedAfter); }
    public Optional<Instant> startedBefore()  { return Optional.ofNullable(startedBefore); }
    public Optional<Instant> finishedAfter()  { return Optional.ofNullable(finishedAfter); }
    public Optional<Instant> finishedBefore() { return Optional.ofNullable(finishedBefore); }
    public SortField sortField()              { return sortField; }
    public SortDirection sortDirection()      { return sortDirection; }
    public int limit()                        { return limit; }
    public int offset()                       { return offset; }

    public static Builder builder() { return new Builder(); }

    /** Matches all records, newest first, up to the default limit (100). */
    public static ExecutionQuery all() { return builder().build(); }

    /**
     * Builds a query via a lambda — used by
     * {@link ExecutionStore#find(Consumer)}.
     *
     * <pre>{@code
     * store.find(q -> q.status(COMPLETED).limit(50));
     * }</pre>
     */
    public static ExecutionQuery of(Consumer<Builder> spec) {
        Builder b = builder();
        spec.accept(b);
        return b.build();
    }

    public static final class Builder {

        private String workflowId;
        private ExecutionStatus status;
        private Instant startedAfter;
        private Instant startedBefore;
        private Instant finishedAfter;
        private Instant finishedBefore;
        private SortField sortField      = SortField.STARTED_AT;
        private SortDirection sortDirection = SortDirection.DESC;
        private int limit  = 100;
        private int offset = 0;

        private Builder() {}

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder status(ExecutionStatus status) {
            this.status = status;
            return this;
        }

        public Builder startedAfter(Instant instant) {
            this.startedAfter = instant;
            return this;
        }

        public Builder startedBefore(Instant instant) {
            this.startedBefore = instant;
            return this;
        }

        public Builder finishedAfter(Instant instant) {
            this.finishedAfter = instant;
            return this;
        }

        public Builder finishedBefore(Instant instant) {
            this.finishedBefore = instant;
            return this;
        }

        /**
         * Sets sort field and direction together.
         *
         * <pre>{@code
         * .orderBy(SortField.STARTED_AT, SortDirection.DESC)
         * }</pre>
         */
        public Builder orderBy(SortField field, SortDirection direction) {
            this.sortField = field;
            this.sortDirection = direction;
            return this;
        }

        /**
         * Maximum records to return per page. Must be between 1 and 1000.
         * Defaults to 100.
         */
        public Builder limit(int limit) {
            if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be between 1 and 1000");
            this.limit = limit;
            return this;
        }

        /** Zero-based page offset. Must be non-negative. */
        public Builder offset(int offset) {
            if (offset < 0) throw new IllegalArgumentException("offset must be >= 0");
            this.offset = offset;
            return this;
        }

        public ExecutionQuery build() {
            // Validate time ranges
            if (startedAfter != null && startedBefore != null && !startedAfter.isBefore(startedBefore)) {
                throw new IllegalArgumentException("startedAfter must be before startedBefore");
            }
            if (finishedAfter != null && finishedBefore != null && !finishedAfter.isBefore(finishedBefore)) {
                throw new IllegalArgumentException("finishedAfter must be before finishedBefore");
            }
            return new ExecutionQuery(this);
        }
    }
}