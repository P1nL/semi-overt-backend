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

/**
 * 网关认证辅助接口。
 * 当前仅承载登出能力：通过把 token 写入 Redis 黑名单，使其在剩余有效期内失效。
 */
@RestController
@RequiredArgsConstructor
public class GatewayAuthController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayJwtHelper jwtHelper;

    /**
     * 登出。
     * 如果 token 缺失或本身已过期，则直接返回成功；否则按剩余有效期写入黑名单。
     */
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

    /**
     * 从 Authorization 头中提取 Bearer token。
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}
