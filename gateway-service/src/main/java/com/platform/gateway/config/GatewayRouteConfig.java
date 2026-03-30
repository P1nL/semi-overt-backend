package com.platform.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * 网关路由配置。
 * 定义对外 URL 到各微服务的映射关系，并统一挂载 Redis 限流过滤器。
 */
@Configuration
public class GatewayRouteConfig {

    /**
     * 注册网关静态路由。
     */
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder,
                                     RedisRateLimiter defaultRedisRateLimiter,
                                     KeyResolver clientRateLimiterKeyResolver) {
        return builder.routes()
                .route("auth-service-public", r -> r
                        .path("/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://auth-service"))
                .route("auth-service-users", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://auth-service"))
                .route("content-service", r -> r
                        .path("/api/v1/home",
                                "/api/v1/categories/**",
                                "/api/v1/articles/**")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://content-service"))
                .route("review-service", r -> r
                        .path("/api/v1/reviews/**")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://review-service"))
                .route("search-service", r -> r
                        .path("/api/v1/search/**")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://search-service"))
                .route("file-service", r -> r
                        .path("/api/v1/uploads/**", "/static/uploads/**")
                        .filters(f -> applyRateLimit(f, defaultRedisRateLimiter, clientRateLimiterKeyResolver))
                        .uri("lb://file-service"))
                .build();
    }

    /**
     * 为路由统一附加限流器。
     */
    private GatewayFilterSpec applyRateLimit(GatewayFilterSpec filters,
                                             RedisRateLimiter rateLimiter,
                                             KeyResolver keyResolver) {
        return filters.requestRateLimiter(config -> {
            config.setRateLimiter(rateLimiter);
            config.setKeyResolver(keyResolver);
            config.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        });
    }
}
