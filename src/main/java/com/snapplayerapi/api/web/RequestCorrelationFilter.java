package com.snapplayerapi.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds request correlation (`X-Request-Id`) and emits a compact access log line.
 *
 * <p>Why this exists:
 * - helps correlate controller/service logs with client reports
 * - keeps the error path observable without full tracing infrastructure
 * - provides a stable hook for future migration to structured logging/tracing systems</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /**
     * Header used for request correlation.
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        long startedAtNanos = System.nanoTime();

        request.setAttribute(HttpObservabilityInterceptor.REQUEST_START_NANOS_ATTR, startedAtNanos);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        MDC.put("requestId", requestId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
            log.info(
                    "http_request requestId={} method={} path={} status={} durationMs={}",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs
            );
            MDC.remove("httpPath");
            MDC.remove("httpMethod");
            MDC.remove("requestId");
        }
    }

    /**
     * Reuses a client-provided request id when present, otherwise generates one.
     */
    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.strip();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
