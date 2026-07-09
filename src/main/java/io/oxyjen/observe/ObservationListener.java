package io.oxyjen.observe;

import io.oxyjen.execution.ExecutionEvent;

/**
 * Receives {@link ExecutionEvent}s emitted by the {@link ObservationBus}.
 *
 * <p>Implementations can be anything that wants to observe execution:
 * a logging exporter, a metrics collector, an execution timeline accumulator,
 * a live dashboard feed, or a test assertion helper.
 *
 * <p>Implementations must be thread-safe the {@link ObservationBus} may
 * call {@link #onEvent} from multiple threads concurrently when parallel
 * graph regions are executing.
 *
 * Implementation should avoid throwing exceptions.
 * Listener failures are handles according to the ObservationBus implementation's
 * error policy and must never affect workflow execution.
 */
@FunctionalInterface
public interface ObservationListener {

    /**
     * Called when an event is emitted during workflow execution.
     *
     * @param event  the event that occurred; never {@code null}
     */
    void onEvent(ExecutionEvent event);
}