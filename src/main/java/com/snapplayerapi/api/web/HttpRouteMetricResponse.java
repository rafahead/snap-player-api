package com.snapplayerapi.api.web;

/**
 * Aggregated HTTP metrics for one `(method, routePattern)` pair.
 */
public record HttpRouteMetricResponse(
        String method,
        String routePattern,
        long requests,
        long errors,
        long status2xx,
        long status4xx,
        long status5xx,
        long statusOther,
        double avgDurationMs,
        long maxDurationMs
) {
}
