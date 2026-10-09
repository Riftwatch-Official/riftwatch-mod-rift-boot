package net.riftwatch.boot.update;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

final class Background {
    private static final long PUMP_MILLIS = 50;
    private final Display display;
    private final AtomicLong lastActivity = new AtomicLong();

    Background(Display display) {
        this.display = display;
    }

    void touch() {
        lastActivity.set(System.nanoTime());
    }

    <T> T run(String name, long stallMillis, Callable<T> work) throws UpdateException {
        touch();
        FutureTask<T> task = new FutureTask<>(work);
        Thread thread = new Thread(task, "Riftwatch-Update-" + name);
        thread.setDaemon(true);
        thread.start();
        long stallNanos = TimeUnit.MILLISECONDS.toNanos(stallMillis);
        while (true) {
            display.pump();
            try {
                return task.get(PUMP_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (System.nanoTime() - lastActivity.get() > stallNanos) {
                    task.cancel(true);
                    throw new UpdateException(name + " stalled for " + stallMillis / 1000 + " s");
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof UpdateException update) {
                    throw update;
                }
                throw new UpdateException(name + " failed: " + UpdateException.describe(cause), cause);
            } catch (InterruptedException e) {
                task.cancel(true);
                Thread.currentThread().interrupt();
                throw new UpdateException(name + " interrupted", e);
            }
        }
    }

    void sleep(long millis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) {
            display.pump();
            try {
                Thread.sleep(PUMP_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
