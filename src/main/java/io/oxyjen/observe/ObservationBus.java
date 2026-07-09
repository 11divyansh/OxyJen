package io.oxyjen.observe;

import io.oxyjen.execution.ExecutionEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe event bus that receives {@link ExecutionEvent}s from the executor
 * and fans them out to all registered {@link ObservationListener}s.
 *
 * <p>One {@code ObservationBus} instance lives on {@code ExecutionRuntime} for
 * the lifetime of a workflow execution. The executor calls {@link #emit} at
 * every observable point (node start, node completion, branch taken, etc.) 
 * it never calls listeners directly.
 *
 * <p><b>Thread safety:</b> {@link #emit} and {@link #addListener} are safe to
 * call from multiple threads concurrently. Listener registration uses a
 * {@link CopyOnWriteArrayList} so that iterating during emit never blocks
 * registrations and vice-versa.
 *
 * <p><b>Failure isolation:</b> a listener that throws is logged at WARNING
 * level and skipped, it never propagates to the executor or affects other
 * listeners. A broken exporter must not crash a running workflow.
 *
 * <p><b>Ordering guarantee:</b> within a single thread, events are delivered to
 * all listeners in registration order before the next event is emitted.
 * Across threads (e.g. parallel graph regions), events from different nodes
 * may interleave, listeners that care about per-execution ordering should
 * use the {@code executionId} and event timestamps to reconstruct sequence.
 */
public final class ObservationBus {

    private static final Logger LOG = Logger.getLogger(ObservationBus.class.getName());

    private final List<ObservationListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive all future events emitted on this bus.
     * Listeners are notified in registration order.
     *
     * @param listener  the listener to add; must not be {@code null}
     */
    public void addListener(ObservationListener listener) {
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously-registered listener. No-op if the listener was
     * never registered or was already removed.
     *
     * @param listener  the listener to remove
     */
    public void removeListener(ObservationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Emits an event to all registered listeners.
     *
     * <p>Called by the executor at every observable execution point. Each
     * listener is called in turn; a listener that throws is logged and skipped
     * without affecting subsequent listeners or the caller.
     *
     * @param event  the event to emit; must not be {@code null}
     */
    public void emit(ExecutionEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        for (ObservationListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        "ObservationListener threw during event dispatch — listener will be skipped. "
                        + "Event: " + event.getClass().getSimpleName()
                        + ", executionId: " + event.executionId(),
                        e);
            }
        }
    }

    /**
     * Returns {@code true} if no listeners are registered. Useful for the
     * executor to skip event construction entirely when nothing is listening,
     * avoiding unnecessary object allocation in hot paths.
     */
    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    /** Returns the number of currently registered listeners. */
    public int listenerCount() {
        return listeners.size();
    }
    
    public void clear() {
        listeners.clear();
    }
}