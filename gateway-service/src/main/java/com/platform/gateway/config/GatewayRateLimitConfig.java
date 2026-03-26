package com.platform.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public RedisRateLimiter defaultRedisRateLimiter(
            @Value("${platform.gateway.rate-limit.replenish-rate:30}") int replenishRate,
            @Value("${platform.gateway.rate-limit.burst-capacity:60}") int burstCapacity,
            @Value("${platform.gateway.rate-limit.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    @Bean
    public KeyResolver clientRateLimiterKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (StringUtils.hasText(userId)) {
                return Mono.just("user:" + userId);
            }

            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                return Mono.just("ip:" + forwardedFor.split(",")[0].trim());
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                return Mono.just("ip:" + remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }
}
