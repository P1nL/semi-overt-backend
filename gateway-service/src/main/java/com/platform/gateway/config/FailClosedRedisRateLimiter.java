package com.platform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.Duration;

/** Preserve the Redis Lua limiter, but never accept its unknown-budget fallback. */
public final class FailClosedRedisRateLimiter extends RedisRateLimiter {
    public FailClosedRedisRateLimiter(int replenishRate, int burstCapacity, int requestedTokens) {
        super(replenishRate, burstCapacity, requestedTokens);
        setIncludeHeaders(true);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        // Gateway 4.1.x catches Redis errors and emits allowed=true with
        // remaining=-1. Treat that sentinel (or a missing value) as unavailable,
        // not a real quota decision. Normal 429 and successful Lua results stay intact.
        return Mono.defer(() -> super.isAllowed(routeId,id))
                .timeout(Duration.ofSeconds(3))
                .flatMap(response -> {
                    String remaining=response.getHeaders().get(getRemainingHeader());
                    try {
                        if(remaining != null && Long.parseLong(remaining) >= 0) return Mono.just(response);
                    } catch(NumberFormatException ignored) { /* fail closed */ }
                    return Mono.error(unavailable());
                })
                .onErrorMap(error -> error instanceof ResponseStatusException ? error : unavailable());
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Rate-limit service temporarily unavailable");
    }
}
