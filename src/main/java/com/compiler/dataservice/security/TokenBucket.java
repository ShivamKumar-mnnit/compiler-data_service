package com.compiler.dataservice.security;

/**
 * Minimal thread-safe token bucket for per-key rate limiting. Hand-rolled
 * (instead of a library) to keep this in-memory-only and dependency-free;
 * swap for a Redis-backed limiter if the service is ever scaled horizontally.
 */
public class TokenBucket {

    private final double capacity;
    private final double refillTokensPerPeriod;
    private final long periodMillis;

    private double tokens;
    private long lastRefillTimestamp;

    public TokenBucket(double capacity, double refillTokensPerPeriod, long periodMillis) {
        this.capacity = capacity;
        this.refillTokensPerPeriod = refillTokensPerPeriod;
        this.periodMillis = periodMillis;
        this.tokens = capacity;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefillTimestamp;
        if (elapsed <= 0) {
            return;
        }
        double tokensToAdd = (elapsed / (double) periodMillis) * refillTokensPerPeriod;
        if (tokensToAdd > 0) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillTimestamp = now;
        }
    }
}
