package io.oxyjen.execution.result;

/** Element was never submitted - window full when deadline hit. */
public final class NotExecuted<T> implements TaskResult<T> {
    private final String reason;
    public NotExecuted(String reason)       { this.reason = reason; }
    public String reason()                  { return reason; }
    @Override public boolean isSuccess()    { return false; }
    @Override public boolean isNotExecuted(){ return true; }
    @Override public String toString()      { return "NotExecuted(" + reason + ")"; }
}