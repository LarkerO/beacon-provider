package com.hydroline.beacon.provider.service.mtr;

import com.hydroline.beacon.provider.BeaconProviderMod;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializes heavy schedule requests so we only run one at a time and keep a fixed delay between them.
 */
final class MtrScheduleRequestQueue {
    private static final long RATE_LIMIT_INTERVAL_MS = Long.getLong("beacon.scheduleRateLimitMs", 400L);
    private static final long REQUEST_TIMEOUT_MS = Long.getLong("beacon.scheduleRequestTimeoutMs", 30_000L);
    private static final int MAX_PENDING_REQUESTS = 64;
    private static final BlockingQueue<Runnable> WORK_QUEUE = new LinkedBlockingQueue<>(MAX_PENDING_REQUESTS);
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        WORK_QUEUE,
        new NamedThreadFactory("beacon-mtr-schedule")
    );

    private static final Object RATE_LOCK = new Object();
    private static volatile long lastExecutionTimeMs = 0L;

    private MtrScheduleRequestQueue() {
    }

    /**
     * Synchronously executes {@code task} after enqueuing it, applying rate limiting and timeouts.
     */
    public static <T> T submit(String label, Callable<T> task)
            throws InterruptedException, ExecutionException, TimeoutException, QueueRejectedException {
        CompletableFuture<T> completion = new CompletableFuture<>();
        try {
            WORKER.execute(() -> {
                applyRateLimit();
                try {
                    completion.complete(task.call());
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }
            });
        } catch (RejectedExecutionException ex) {
            BeaconProviderMod.LOGGER.warn("Schedule queue full ({}). Pending={}", label, WORK_QUEUE.size());
            throw new QueueRejectedException(label, ex);
        }
        return completion.get(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static void applyRateLimit() {
        synchronized (RATE_LOCK) {
            long now = System.currentTimeMillis();
            long elapsed = now - lastExecutionTimeMs;
            if (elapsed < RATE_LIMIT_INTERVAL_MS) {
                try {
                    Thread.sleep(RATE_LIMIT_INTERVAL_MS - elapsed);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            lastExecutionTimeMs = System.currentTimeMillis();
        }
    }

    static final class QueueRejectedException extends Exception {
        QueueRejectedException(String label, RejectedExecutionException cause) {
            super(label + " schedule queue is full", cause);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, baseName + '-' + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
