package io.hc.heavenlycoupon.api;

import io.hc.heavenlycoupon.api.dto.TrafficStartRequest;
import io.hc.heavenlycoupon.api.dto.TrafficStatusResponse;
import io.hc.heavenlycoupon.traffic.TrafficGeneratorService;
import io.hc.heavenlycoupon.traffic.TrafficStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/traffic")
public class TrafficController {

    private final TrafficGeneratorService trafficGeneratorService;

    @PostMapping("/start")
    public ResponseEntity<TrafficStatusResponse> start(@RequestBody TrafficStartRequest request) {
        if (request == null || request.targetQps() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        int concurrency = request.concurrency() != null && request.concurrency() > 0
                ? request.concurrency()
                : Runtime.getRuntime().availableProcessors();
        long durationSeconds = request.durationSeconds() != null && request.durationSeconds() > 0
                ? request.durationSeconds()
                : 30L;

        Optional<TrafficStatus> started = trafficGeneratorService.start(
                request.targetQps(),
                concurrency,
                Duration.ofSeconds(durationSeconds)
        );

        if (started.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return ResponseEntity.accepted().body(toResponse(started.get()));
    }

    @PostMapping("/stop")
    public ResponseEntity<Void> stop() {
        boolean stopped = trafficGeneratorService.stop();
        if (!stopped) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public ResponseEntity<TrafficStatusResponse> status() {
        return trafficGeneratorService.status()
                .map(status -> ResponseEntity.ok(toResponse(status)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static TrafficStatusResponse toResponse(TrafficStatus status) {
        return new TrafficStatusResponse(
                status.targetQps(),
                status.concurrency(),
                status.duration(),
                status.running(),
                status.startedAt(),
                status.elapsed(),
                status.sentRequests()
        );
    }
}
