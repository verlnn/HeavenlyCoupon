package io.hc.heavenlycoupon.traffic;

import io.hc.heavenlycoupon.coupon.CouponRequestProcessor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TrafficGeneratorService {

    private final CouponRequestProcessor processor;
    private final AtomicReference<TrafficRun> currentRun = new AtomicReference<>();

    public TrafficGeneratorService(CouponRequestProcessor processor) {
        this.processor = processor;
    }

    public Optional<TrafficStatus> start(long targetQps, int concurrency, Duration duration) {
        TrafficRun run = new TrafficRun(processor, targetQps, concurrency, duration);
        if (!currentRun.compareAndSet(null, run)) {
            return Optional.empty();
        }

        run.start();
        Thread monitor = new Thread(() -> {
            try {
                run.awaitCompletion();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                currentRun.compareAndSet(run, null);
            }
        }, "traffic-run-monitor");
        monitor.setDaemon(true);
        monitor.start();
        return Optional.of(toStatus(run));
    }

    public boolean stop() {
        TrafficRun run = currentRun.get();
        if (run == null) {
            return false;
        }
        run.stop();
        currentRun.compareAndSet(run, null);
        return true;
    }

    public Optional<TrafficStatus> status() {
        TrafficRun run = currentRun.get();
        if (run == null) {
            return Optional.empty();
        }
        return Optional.of(toStatus(run));
    }

    private static TrafficStatus toStatus(TrafficRun run) {
        Instant startedAt = run.getStartedAt();
        Duration elapsed = Duration.between(startedAt, Instant.now());
        return new TrafficStatus(
                run.getTargetQps(),
                run.getConcurrency(),
                run.getDuration(),
                run.isRunning(),
                startedAt,
                elapsed,
                run.getSentRequests()
        );
    }
}
