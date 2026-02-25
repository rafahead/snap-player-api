package com.snapplayerapi.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the HTTP observability interceptor for all routes.
 */
@Configuration
public class WebObservabilityConfig implements WebMvcConfigurer {

    private final HttpObservabilityInterceptor httpObservabilityInterceptor;

    public WebObservabilityConfig(HttpObservabilityInterceptor httpObservabilityInterceptor) {
        this.httpObservabilityInterceptor = httpObservabilityInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpObservabilityInterceptor);
    }
}
