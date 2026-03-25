package com.platform.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service-public", r -> r
                        .path("/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password")
                        .uri("lb://auth-service"))
                .route("auth-service-users", r -> r
                        .path("/api/v1/users/**")
                        .uri("lb://auth-service"))
                .route("content-service", r -> r
                        .path("/api/v1/home",
                                "/api/v1/categories/**",
                                "/api/v1/articles/**")
                        .uri("lb://content-service"))
                .route("review-service", r -> r
                        .path("/api/v1/reviews/**")
                        .uri("lb://review-service"))
                .route("search-service", r -> r
                        .path("/api/v1/search/**")
                        .uri("lb://search-service"))
                .route("file-service", r -> r
                        .path("/api/v1/uploads/**", "/static/uploads/**")
                        .uri("lb://file-service"))
                .build();
    }
}
