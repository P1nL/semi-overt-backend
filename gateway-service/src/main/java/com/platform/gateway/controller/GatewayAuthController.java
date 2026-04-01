package com.platform.gateway.controller;

import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 缃戝叧璁よ瘉杈呭姪鎺ュ彛銆?
 * 褰撳墠浠呮壙杞界櫥鍑鸿兘鍔涳細閫氳繃鎶?token 鍐欏叆 Redis 榛戝悕鍗曪紝浣垮叾鍦ㄥ墿浣欐湁鏁堟湡鍐呭け鏁堛€?
 */
@RestController
@RequiredArgsConstructor
public class GatewayAuthController {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayJwtHelper jwtHelper;

    /**
     * 鐧诲嚭銆?
     * 濡傛灉 token 缂哄け鎴栨湰韬凡杩囨湡锛屽垯鐩存帴杩斿洖鎴愬姛锛涘惁鍒欐寜鍓╀綑鏈夋晥鏈熷啓鍏ラ粦鍚嶅崟銆?
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
     * 浠?Authorization 澶翠腑鎻愬彇 Bearer token銆?
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return null;
    }
}

