package io.homeassistant.binding.danfoss.internal;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link SessionScheduler} backed by one dedicated thread. */
public final class ExecutorSessionScheduler implements SessionScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ExecutorSessionScheduler.class);

    private final ScheduledThreadPoolExecutor executor;

    public ExecutorSessionScheduler(String threadName) {
        executor = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(false);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void execute(Runnable task) {
        executor.execute(guard(task));
    }

    @Override
    public Cancellable schedule(Runnable task, long delayMs) {
        ScheduledFuture<?> f = executor.schedule(guard(task), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        return () -> f.cancel(false);
    }

    public void shutdown() {
        executor.shutdown();
    }

    // A task that throws must never go unnoticed: before this rewrite an exception
    // escaping an executor task silently ended that thermostat's reconnect loop.
    private static Runnable guard(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.error("Unexpected error in a thermostat session task", t);
            }
        };
    }
}
