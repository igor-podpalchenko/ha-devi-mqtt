package io.homeassistant.binding.danfoss.internal;

/**
 * A serial executor with a clock: every task of one thermostat session runs on it,
 * one at a time, so the session state needs no locking. Abstracted so tests can
 * drive time deterministically.
 */
public interface SessionScheduler {

    interface Cancellable {
        void cancel();
    }

    long now();

    /** Runs the task soon, after already queued tasks. May throw RejectedExecutionException once shut down. */
    void execute(Runnable task);

    /** Runs the task after the delay, serialised with every other task. */
    Cancellable schedule(Runnable task, long delayMs);
}
