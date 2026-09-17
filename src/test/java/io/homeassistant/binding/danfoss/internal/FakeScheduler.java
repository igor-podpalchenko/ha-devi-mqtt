package io.homeassistant.binding.danfoss.internal;

import java.util.Comparator;
import java.util.PriorityQueue;

/** Deterministic virtual-time {@link SessionScheduler} for tests. */
final class FakeScheduler implements SessionScheduler {

    private static final class Task {
        final long at;
        final long seq;
        final Runnable run;
        boolean cancelled;

        Task(long at, long seq, Runnable run) {
            this.at = at;
            this.seq = seq;
            this.run = run;
        }
    }

    private final PriorityQueue<Task> queue = new PriorityQueue<>(
            Comparator.comparingLong((Task t) -> t.at).thenComparingLong(t -> t.seq));
    private long seq;
    // volatile: read by connect attempts running on another thread in some tests
    volatile long now = 1_700_000_000_000L;

    @Override
    public long now() {
        return now;
    }

    @Override
    public void execute(Runnable task) {
        queue.add(new Task(now, seq++, task));
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        Task t = new Task(now + Math.max(0, delayMs), seq++, task);
        queue.add(t);
        return () -> t.cancelled = true;
    }

    /** Runs everything due now (including tasks those tasks enqueue for now). */
    void runDue() {
        advance(0);
    }

    /** Moves time forward, running due tasks in order. */
    void advance(long ms) {
        long target = now + ms;
        Task t;
        while ((t = queue.peek()) != null && t.at <= target) {
            queue.poll();
            now = Math.max(now, t.at);
            if (!t.cancelled) {
                t.run.run();
            }
        }
        now = target;
    }
}
