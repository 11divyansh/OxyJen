package io.oxyjen.execution.result;

/** Element was submitted and running but cancelled (timeout or fail-fast). */
public final class Cancelled<T> implements TaskResult<T> {
    private final String reason;
    public Cancelled(String reason)        { this.reason = reason; }
    public String reason()                 { return reason; }
    @Override public boolean isSuccess()   { return false; }
    @Override public boolean isCancelled() { return true; }
    @Override public String toString()     { return "Cancelled(" + reason + ")"; }
}