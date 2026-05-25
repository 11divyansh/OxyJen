package io.oxyjen.execution.result;

public sealed interface TaskResult<T> permits Success, Failure, Cancelled, NotExecuted {
	boolean isSuccess();
	default boolean isFailure() { return false;}
	default boolean isCancelled() { return false; }
	default boolean isNotExecuted() { return false; }
	/* Execution lifecycle semantics */
    default boolean wasExecuted() { return isSuccess() || isFailure() || isCancelled(); }
    default boolean isCompleted() { return isSuccess() || isFailure(); }
    default boolean isTerminal() { return true; }
}