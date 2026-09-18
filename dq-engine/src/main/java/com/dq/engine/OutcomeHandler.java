package com.dq.engine;

import com.dq.engine.model.Outcome;

/**
 * Receives every outcome as the engine produces it.
 *
 * <p>The engine pushes rather than returning a list, because a returned list would hold
 * every outcome in memory — tens of millions of objects at a million records. Exceptions
 * thrown here are not caught: a handler that cannot accept outcomes is a broken host, and
 * continuing would report a run that wrote nothing.
 *
 * <p>Called from a single thread at a time, so implementations need no synchronisation.
 */
public interface OutcomeHandler {

    void handle(Outcome outcome);

    /**
     * Called at each batch boundary and once at the end — this is what the configurable
     * batch size controls. Nothing is buffered by the engine itself.
     */
    default void flush() {
    }
}
