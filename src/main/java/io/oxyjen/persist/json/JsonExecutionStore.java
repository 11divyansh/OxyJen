package io.oxyjen.persist.json;

import java.nio.file.Path;
import java.util.Objects;

import io.oxyjen.persist.ExecutionStore;

/**
 * {@link ExecutionStore} implementation that persists each
 * {@link io.oxyjen.execution.ExecutionRecord} as a single JSON file.
 *
 * <p>Each execution is stored independently using its executionId as
 * the filename, making executions immutable and individually addressable.
 *
 * <pre>
 * executions/
 * ├── 3b1c8d.json
 * ├── 9f42aa.json
 * └── e7b1d3.json
 * </pre>
 *
 * <p>This implementation is intended for:
 *
 * <ul>
 *     <li>local development</li>
 *     <li>desktop applications</li>
 *     <li>debugging</li>
 *     <li>execution replay</li>
 *     <li>CI artifact storage</li>
 * </ul>
 *
 * <p>It is not intended to replace a relational database for high-volume
 * production workloads.
 */
public final class JsonExecutionStore implements ExecutionStore {

    /**
     * Directory where execution JSON files are stored.
     */
    private final Path directory;

    /**
     * Creates a store rooted at the given directory.
     *
     * @param directory directory used to persist execution JSON files
     */
    public JsonExecutionStore(Path directory) {
        this.directory = Objects.requireNonNull(directory,
                "directory must not be null");
    }

    /**
     * Root directory containing execution JSON files.
     */
    public Path directory() {
        return directory;
    }
}