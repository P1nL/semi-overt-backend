package com.platform.gateway.controller;

import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class GatewayAuthController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayJwtHelper jwtHelper;

    @PostMapping("/api/v1/auth/logout")
    public Mono<Result<Void>> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return Mono.just(Result.ok());
        }

        long remainingMillis = jwtHelper.getRemainingMillis(token);
        if (remainingMillis <= 0) {
            return Mono.just(Result.ok());
        }

        return redisTemplate.opsForValue()
                .set("jwt:blacklist:" + token, "1", Duration.ofMillis(remainingMillis))
                .thenReturn(Result.ok());
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
