package com.snapplayerapi.api.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint exposing the in-memory HTTP metrics snapshot.
 *
 * <p>This endpoint is intended for local/dev and early operational checks. It is deliberately
 * simple and read-only. In later phases this can be replaced by Actuator/Prometheus/OpenTelemetry
 * without affecting the rest of the application behavior.</p>
 */
@RestController
@RequestMapping("/internal/observability")
public class HttpObservabilityController {

    private final HttpObservabilityRegistry registry;
    private final SnapJobObservabilityRegistry snapJobObservabilityRegistry;

    public HttpObservabilityController(
            HttpObservabilityRegistry registry,
            SnapJobObservabilityRegistry snapJobObservabilityRegistry
    ) {
        this.registry = registry;
        this.snapJobObservabilityRegistry = snapJobObservabilityRegistry;
    }

    /**
     * Returns aggregated HTTP request metrics grouped by method + route template.
     */
    @GetMapping("/http-metrics")
    public ResponseEntity<HttpObservabilitySnapshotResponse> httpMetrics() {
        return ResponseEntity.ok(registry.snapshot());
    }

    /**
     * Returns worker/job telemetry for async snap processing (Entrega 4).
     */
    @GetMapping("/snap-job-metrics")
    public ResponseEntity<SnapJobObservabilitySnapshotResponse> snapJobMetrics() {
        return ResponseEntity.ok(snapJobObservabilityRegistry.snapshot());
    }
}
