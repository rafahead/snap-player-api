package com.snapplayerapi.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards all {@code /internal/**} routes with an optional static token.
 *
 * <p>When {@code app.internal.accessToken} is blank (default), the endpoints are open —
 * suitable for local development and environments where network-level restriction (nginx/firewall)
 * is used instead. When set, the request must supply the matching value via the
 * {@code X-Internal-Token} header. Comparison is timing-safe to resist enumeration.</p>
 *
 * <p>Production recommendation: set {@code app.internal.accessToken} via environment variable and
 * additionally restrict access to the {@code /internal} path at the reverse-proxy level.</p>
 */
@Component
public class InternalApiTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalApiTokenInterceptor.class);
    private static final String HEADER = "X-Internal-Token";

    private final String accessToken;

    public InternalApiTokenInterceptor(@Value("${app.internal.accessToken:}") String accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (accessToken.isBlank()) {
            return true;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !MessageDigest.isEqual(
                accessToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            log.warn("internal_access_denied path={} ip={}", request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"message\":\"Internal API token required\"}");
            return false;
        }
        return true;
    }
}
