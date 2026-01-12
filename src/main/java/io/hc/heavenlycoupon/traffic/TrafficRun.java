package io.hc.heavenlycoupon.traffic;

import io.hc.heavenlycoupon.coupon.CouponRequest;
import io.hc.heavenlycoupon.coupon.CouponRequestProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;

final class TrafficRun {

    private static final CouponRequest DUMMY_REQUEST =
            new CouponRequest("traffic", new UUID(0L, 0L));

    private final CouponRequestProcessor processor;
    private final long targetQps;
    private final int concurrency;
    private final Duration duration;
    private final Instant startedAt;
    private final LongAdder sentRequests = new LongAdder();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ExecutorService executor;
    private final CountDownLatch doneLatch;

    TrafficRun(CouponRequestProcessor processor, long targetQps, int concurrency, Duration duration) {
        this.processor = processor;
        this.targetQps = targetQps;
        this.concurrency = concurrency;
        this.duration = duration;
        this.startedAt = Instant.now();
        this.executor = Executors.newFixedThreadPool(concurrency);
        this.doneLatch = new CountDownLatch(concurrency);
    }

    void start() {
        long baseQps = targetQps / concurrency;
        long remainder = targetQps % concurrency;
        long durationNanos = duration.toNanos();
        long startNanos = System.nanoTime();
        long endNanos = startNanos + durationNanos;

        for (int i = 0; i < concurrency; i++) {
            long perThreadQps = baseQps + (i < remainder ? 1 : 0);
            executor.execute(() -> runWorker(perThreadQps, endNanos));
        }
    }

    void stop() {
        stopRequested.set(true);
        executor.shutdownNow();
    }

    boolean isRunning() {
        return doneLatch.getCount() > 0 && !stopRequested.get();
    }

    void awaitCompletion() throws InterruptedException {
        doneLatch.await();
    }

    long getTargetQps() {
        return targetQps;
    }

    int getConcurrency() {
        return concurrency;
    }

    Duration getDuration() {
        return duration;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    long getSentRequests() {
        return sentRequests.sum();
    }

    private void runWorker(long perThreadQps, long endNanos) {
        try {
            if (perThreadQps <= 0) {
                return;
            }

            if (perThreadQps < 1_000) {
                runLowQps(perThreadQps, endNanos);
            } else {
                runHighQps(perThreadQps, endNanos);
            }
        } finally {
            doneLatch.countDown();
        }
    }

    private void runLowQps(long perThreadQps, long endNanos) {
        long intervalNanos = 1_000_000_000L / perThreadQps;
        long nextTick = System.nanoTime();
        while (!stopRequested.get()) {
            if (System.nanoTime() >= endNanos) {
                return;
            }
            processor.process(DUMMY_REQUEST);
            sentRequests.increment();
            nextTick += intervalNanos;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
    }

    private void runHighQps(long perThreadQps, long endNanos) {
        long batchSize = perThreadQps / 1_000;
        long remainder = perThreadQps % 1_000;
        long remainderAccumulator = 0L;
        long nextTick = System.nanoTime();
        while (!stopRequested.get()) {
            if (System.nanoTime() >= endNanos) {
                return;
            }

            long calls = batchSize;
            remainderAccumulator += remainder;
            if (remainderAccumulator >= 1_000) {
                calls += 1;
                remainderAccumulator -= 1_000;
            }

            for (long i = 0; i < calls; i++) {
                processor.process(DUMMY_REQUEST);
                sentRequests.increment();
            }

            nextTick += 1_000_000L;
            long sleepNanos = nextTick - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
    }
}
