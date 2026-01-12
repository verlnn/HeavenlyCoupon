package io.hc.heavenlycoupon.api.dto;

public record TrafficStartRequest(
        long targetQps,
        Integer concurrency,
        Long durationSeconds
) {
}
