package io.hc.heavenlycoupon.traffic;

import java.time.Duration;
import java.time.Instant;

public record TrafficStatus(
        long targetQps,
        int concurrency,
        Duration duration,
        boolean running,
        Instant startedAt,
        Duration elapsed,
        long sentRequests
) {
}
