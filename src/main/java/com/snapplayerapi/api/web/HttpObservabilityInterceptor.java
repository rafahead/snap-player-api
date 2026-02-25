package com.snapplayerapi.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records low-cardinality HTTP metrics after each request completes.
 *
 * <p>The interceptor runs after route matching, so it can read Spring's
 * `BEST_MATCHING_PATTERN_ATTRIBUTE` and aggregate metrics by route template instead of raw URI.</p>
 */
@Component
public class HttpObservabilityInterceptor implements HandlerInterceptor {

    /**
     * Request attribute populated by {@link RequestCorrelationFilter} with the start time.
     */
    public static final String REQUEST_START_NANOS_ATTR = "snapplayer.requestStartNanos";

    private final HttpObservabilityRegistry registry;

    public HttpObservabilityInterceptor(HttpObservabilityRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        Object startedAtRaw = request.getAttribute(REQUEST_START_NANOS_ATTR);
        long durationMs = 0L;
        if (startedAtRaw instanceof Long startedAt) {
            durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAt));
        }

        Object routePatternAttr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String routePattern = routePatternAttr instanceof String value ? value : request.getRequestURI();
        registry.record(request.getMethod(), routePattern, response.getStatus(), durationMs);
    }
}
