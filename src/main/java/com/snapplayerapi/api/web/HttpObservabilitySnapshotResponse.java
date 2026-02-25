package com.snapplayerapi.api.web;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Top-level response for the internal HTTP observability snapshot endpoint.
 */
public record HttpObservabilitySnapshotResponse(
        OffsetDateTime generatedAt,
        long totalRequests,
        long totalErrors,
        int routesCount,
        List<HttpRouteMetricResponse> routes
) {
}
