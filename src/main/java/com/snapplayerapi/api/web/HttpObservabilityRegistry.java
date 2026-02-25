package com.snapplayerapi.api.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * In-memory HTTP metrics registry used as the "observabilidade mínima" for Entrega 3.
 *
 * <p>This component intentionally avoids external dependencies (Prometheus, Actuator, OTEL) while
 * still giving the team a quick operational view during local development and early deployments:
 * request counts, error counts and latency aggregates grouped by method + route pattern.</p>
 */
@Component
public class HttpObservabilityRegistry {

    /**
     * Aggregates keyed by `HTTP_METHOD + space + routePattern`.
     *
     * <p>Example key: `POST /v2/snaps`.</p>
     */
    private final ConcurrentMap<String, RouteAggregate> routes = new ConcurrentHashMap<>();

    /**
     * Records one completed HTTP request.
     *
     * <p>`routePattern` should preferably be the Spring route template (`/v2/snaps/{id}`) instead of
     * the raw URI so metrics remain low-cardinality and operationally useful.</p>
     */
    public void record(String method, String routePattern, int statusCode, long durationMs) {
        String safeMethod = method == null ? "UNKNOWN" : method;
        String safeRoutePattern = (routePattern == null || routePattern.isBlank()) ? "UNMAPPED" : routePattern;
        String key = safeMethod + " " + safeRoutePattern;
        routes.computeIfAbsent(key, ignored -> new RouteAggregate(safeMethod, safeRoutePattern))
                .record(statusCode, durationMs);
    }

    /**
     * Returns a stable snapshot suitable for JSON serialization and human inspection.
     */
    public HttpObservabilitySnapshotResponse snapshot() {
        List<HttpRouteMetricResponse> routeMetrics = new ArrayList<>();
        long totalRequests = 0;
        long totalErrors = 0;

        for (RouteAggregate aggregate : routes.values()) {
            HttpRouteMetricResponse route = aggregate.toResponse();
            routeMetrics.add(route);
            totalRequests += route.requests();
            totalErrors += route.errors();
        }

        routeMetrics.sort(Comparator
                .comparingLong(HttpRouteMetricResponse::requests).reversed()
                .thenComparing(HttpRouteMetricResponse::method)
                .thenComparing(HttpRouteMetricResponse::routePattern));

        return new HttpObservabilitySnapshotResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                totalRequests,
                totalErrors,
                routeMetrics.size(),
                routeMetrics
        );
    }

    /**
     * Thread-safe mutable accumulator for one `(method, routePattern)` pair.
     */
    private static final class RouteAggregate {
        private final String method;
        private final String routePattern;
        private final LongAdder requests = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder totalDurationMs = new LongAdder();
        private final AtomicLong maxDurationMs = new AtomicLong();
        private final LongAdder status2xx = new LongAdder();
        private final LongAdder status4xx = new LongAdder();
        private final LongAdder status5xx = new LongAdder();
        private final LongAdder statusOther = new LongAdder();

        private RouteAggregate(String method, String routePattern) {
            this.method = method;
            this.routePattern = routePattern;
        }

        /**
         * Updates counters and latency aggregates from a single request completion event.
         */
        private void record(int statusCode, long durationMs) {
            requests.increment();
            totalDurationMs.add(Math.max(durationMs, 0L));
            if (statusCode >= 400) {
                errors.increment();
            }
            if (statusCode >= 200 && statusCode < 300) {
                status2xx.increment();
            } else if (statusCode >= 400 && statusCode < 500) {
                status4xx.increment();
            } else if (statusCode >= 500 && statusCode < 600) {
                status5xx.increment();
            } else {
                statusOther.increment();
            }

            long safeDuration = Math.max(durationMs, 0L);
            maxDurationMs.accumulateAndGet(safeDuration, Math::max);
        }

        /**
         * Materializes immutable response DTO with derived fields (`avgDurationMs`).
         */
        private HttpRouteMetricResponse toResponse() {
            long requestCount = requests.sum();
            long totalMs = totalDurationMs.sum();
            double avgMs = requestCount == 0 ? 0.0 : (double) totalMs / (double) requestCount;

            return new HttpRouteMetricResponse(
                    method,
                    routePattern,
                    requestCount,
                    errors.sum(),
                    status2xx.sum(),
                    status4xx.sum(),
                    status5xx.sum(),
                    statusOther.sum(),
                    avgMs,
                    maxDurationMs.get()
            );
        }
    }
}
