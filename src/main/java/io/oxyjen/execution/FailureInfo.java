package io.oxyjen.execution;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Structured information about a node failure.
 *
 * <p>Replaces separate {@code String errorType}/{@code errorMessage} fields.
 * Keeping the stack trace alongside type and message, rather than discarding
 * it, means replay tooling can later render a full failure chain
 * ("Node X → IllegalArgumentException → ... → Caused by: ...") without any
 * change to {@link ExecutionEvent.NodeFailed} or {@link ExecutionEvent.RetryAttempt}.
 *
 * @param type        fully-qualified class name of the thrown exception
 * @param message     exception message, may be {@code null}
 * @param stackTrace  rendered stack trace (including cause chain), may be {@code null}
 *                    if stack capture is disabled for performance reasons
 */
public record FailureInfo(
        String type,
        String message,
        String stackTrace
) {

    /** Builds a {@link FailureInfo} from a live {@link Throwable}, capturing its full stack trace. */
    public static FailureInfo from(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return new FailureInfo(t.getClass().getName(), t.getMessage(), sw.toString());
    }
}