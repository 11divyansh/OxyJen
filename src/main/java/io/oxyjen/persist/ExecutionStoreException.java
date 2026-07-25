package io.oxyjen.persist;

/**
 * Thrown when an {@link ExecutionStore} operation fails due to a storage-level
 * error, I/O failure, JDBC error, serialization problem, etc.
 *
 * <p>Does not extend {@link RuntimeException} subclasses that would suggest
 * the workflow itself failed. A persistence failure is a separate concern
 * from execution failure, callers should catch this and decide independently
 * whether to retry, log, or alert, without affecting the workflow result.
 */
public class ExecutionStoreException extends RuntimeException {

    public ExecutionStoreException(String message) {
        super(message);
    }

    public ExecutionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}