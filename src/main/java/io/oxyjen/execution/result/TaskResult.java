package io.oxyjen.execution.result;

public sealed interface TaskResult<T> permits Success, Failure, Cancelled, NotExecuted {
	boolean isSuccess();
	default boolean isFailure() { return false;}
	default boolean isCancelled() { return false; }
	default boolean isNotExecuted() { return false; }
}