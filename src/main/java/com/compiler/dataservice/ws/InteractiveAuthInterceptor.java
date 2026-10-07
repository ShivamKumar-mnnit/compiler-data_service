package com.compiler.dataservice.ws;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Same shared-secret check as {@link com.compiler.dataservice.security.ApiKeyAuthFilter},
 * applied to the WebSocket handshake instead of a servlet filter chain
 * (the upgrade request never reaches /api/*, so that filter wouldn't see it).
 * Accepts the key either as an "Authorization" header (server-to-server
 * callers) or an "apiKey" query parameter, since browsers' WebSocket API
 * can't set custom headers on the handshake.
 */
public class InteractiveAuthInterceptor implements HandshakeInterceptor {

    private final Set<String> validKeys;

    public InteractiveAuthInterceptor(List<String> keys) {
        this.validKeys = new HashSet<>(keys);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String key = request.getHeaders().getFirst("Authorization");
        if ((key == null || key.isBlank()) && request instanceof ServletServerHttpRequest servletRequest) {
            key = servletRequest.getServletRequest().getParameter("apiKey");
        }

        if (key == null || key.isBlank() || !validKeys.contains(key)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do.
    }
}
