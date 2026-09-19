package com.compiler.dataservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rejects any request under /api/* that doesn't carry a recognised secret in
 * the "Authorization" header. This is a shared-secret check, not OAuth/JWT --
 * intended for server-to-server calls from our own backend, not browsers.
 */
public class ApiKeyAuthFilter extends GenericFilterBean {

    public static final String API_KEY_ATTRIBUTE = "apiKey";

    private final Set<String> validKeys;

    public ApiKeyAuthFilter(List<String> keys) {
        this.validKeys = new HashSet<>(keys);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String key = httpRequest.getHeader("Authorization");
        if (key == null || key.isBlank() || !validKeys.contains(key)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Missing or invalid API key\"}");
            return;
        }

        httpRequest.setAttribute(API_KEY_ATTRIBUTE, key);
        chain.doFilter(request, response);
    }
}
