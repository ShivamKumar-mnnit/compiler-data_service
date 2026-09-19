package com.compiler.dataservice.security;

import com.compiler.dataservice.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-API-key request throttle, applied after {@link ApiKeyAuthFilter} has
 * already validated the key and stashed it on the request. Keeps a single
 * misbehaving/overeager caller from starving the compile queue for everyone
 * else sharing this server.
 */
public class RateLimitFilter extends GenericFilterBean {

    private final AppProperties.RateLimit rateLimitConfig;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties.RateLimit rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String apiKey = (String) httpRequest.getAttribute(ApiKeyAuthFilter.API_KEY_ATTRIBUTE);
        if (apiKey == null) {
            // Auth filter should have already rejected this; fail closed.
            apiKey = "unknown";
        }

        TokenBucket bucket = buckets.computeIfAbsent(apiKey, k -> new TokenBucket(
                rateLimitConfig.getCapacity(),
                rateLimitConfig.getRefillTokens(),
                rateLimitConfig.getRefillPeriodSeconds() * 1000L
        ));

        if (!bucket.tryConsume()) {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded, please slow down\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
