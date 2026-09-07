package br.com.droidboaoferta;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One pending or running check, with a reusable freshness window for screen resumes. */
final class CoalescingCheckScheduler {
    private static final ThreadLocal<java.util.function.BooleanSupplier> CURRENT = new ThreadLocal<>();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> next;
    private Runnable check;
    private long intervalMs;
    private long generation;
    private long lastCompletedNanos;
    private boolean running;
    private boolean requested;
    private Runnable requestedAction;

    synchronized void start(Runnable check, long intervalMs) {
        start(check, intervalMs, null);
    }

    synchronized void start(Runnable check, long intervalMs, Runnable initialAction) {
        if (executor != null) return;
        this.check = check;
        this.intervalMs = intervalMs;
        this.requestedAction = initialAction;
        executor = Executors.newSingleThreadScheduledExecutor();
        schedule(0);
    }

    synchronized boolean isStarted() { return executor != null; }

    synchronized void request(long freshnessMs) {
        request(freshnessMs, check);
    }

    synchronized void request(long freshnessMs, Runnable action) {
        if (executor == null) return;
        if (running) {
            if (freshnessMs == 0) { requested = true; requestedAction = action; }
            return;
        }
        if (freshnessMs > 0 && lastCompletedNanos > 0
                && System.nanoTime() - lastCompletedNanos < TimeUnit.MILLISECONDS.toNanos(freshnessMs)) return;
        if (next != null) next.cancel(false);
        requestedAction = action;
        schedule(0);
    }

    synchronized void stop() {
        generation++;
        if (executor != null) executor.shutdownNow();
        executor = null;
        next = null;
        running = false;
        requested = false;
        requestedAction = null;
        lastCompletedNanos = 0;
    }

    private void schedule(long delayMs) {
        long token = ++generation;
        next = executor.schedule(() -> run(token), delayMs, TimeUnit.MILLISECONDS);
    }

    private void run(long token) {
        Runnable action;
        synchronized (this) {
            if (executor == null || token != generation) return;
            running = true;
            action = requestedAction == null ? check : requestedAction;
            requestedAction = null;
        }
        CURRENT.set(() -> isCurrent(token));
        try {
            action.run();
        } finally {
            CURRENT.remove();
            synchronized (this) {
                if (executor != null && token == generation) {
                    running = false;
                    lastCompletedNanos = System.nanoTime();
                    boolean again = requested;
                    requested = false;
                    schedule(again ? 0 : intervalMs);
                }
            }
        }
    }

    private synchronized boolean isCurrent(long token) { return executor != null && token == generation; }
    static boolean isCurrentRun() {
        java.util.function.BooleanSupplier valid = CURRENT.get();
        return valid == null || valid.getAsBoolean();
    }
}
