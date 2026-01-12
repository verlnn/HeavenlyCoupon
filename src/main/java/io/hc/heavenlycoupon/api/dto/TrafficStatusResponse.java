package io.hc.heavenlycoupon.api.dto;

import java.time.Duration;
import java.time.Instant;

public record TrafficStatusResponse(
        long targetQps,
        int concurrency,
        Duration duration,
        boolean running,
        Instant startedAt,
        Duration elapsed,
        long sentRequests
) {
}
