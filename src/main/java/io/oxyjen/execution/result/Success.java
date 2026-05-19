package io.oxyjen.execution.result;

public final class Success<T> implements TaskResult<T> {
	private final T value;
    public Success(T value) { this.value = value; }  // null is a valid success value
    public T value() { return value; }
    @Override public boolean isSuccess() { return true; }
    @Override public String toString() { return "Success(" + value + ")"; }
}