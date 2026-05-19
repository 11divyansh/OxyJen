package io.oxyjen.execution.result;

import java.util.Objects;

public final class Failure<T> implements TaskResult<T> {
    private final Throwable error;
    public Failure(Throwable error) { this.error = Objects.requireNonNull(error); }
    public Throwable error() { return error; }
    @Override public boolean isSuccess() { return false; }
    @Override public boolean isFailure() { return true; }
    @Override public String toString() { return "Failure(" + error.getMessage() + ")"; }
}