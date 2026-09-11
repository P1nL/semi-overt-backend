package com.platform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.gateway.security.GatewayJwtHelper.JwtUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiCompatibilityWebFilterTest {

    private ApiCompatibilityWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiCompatibilityWebFilter();
    }

    @Test
    void normalizesBeforeRouteMatchingAndPreservesMethodAndRawQuery() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST,
                "/api/review/42/decision?reason=needs%2Frevision&tag=a&tag=b");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        })).verifyComplete();

        assertThat(forwarded.get().getRequest().getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(forwarded.get().getRequest().getURI().getRawPath())
                .isEqualTo("/api/v1/reviews/42/action");
        assertThat(forwarded.get().getRequest().getURI().getRawQuery())
                .isEqualTo("reason=needs%2Frevision&tag=a&tag=b");
    }

    @Test
    void normalizesTheSupportedAliases() {
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/api/review/42/decision-status"))
                .isEqualTo("/api/v1/reviews/42/decision-status");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/api/review/pending"))
                .isEqualTo("/api/v1/reviews/pending");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/api/v1/review/7/logs"))
                .isEqualTo("/api/v1/reviews/7/logs");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/upload"))
                .isEqualTo("/api/v1/uploads/images");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/api/v1/search"))
                .isEqualTo("/api/v1/search/articles");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.PUT, "/api/users/me"))
                .isEqualTo("/api/v1/users/me/profile");
    }

    @Test
    void respectsMethodAndExactPathBoundariesWithoutDecoding() {
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/review/42/decision-status"))
                .isEqualTo("/api/v1/review/42/decision-status");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/search"))
                .isEqualTo("/api/v1/search");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/users/me"))
                .isEqualTo("/api/v1/users/me");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/api/upload"))
                .isEqualTo("/api/v1/upload");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/review/7/decision/extra"))
                .isEqualTo("/api/v1/review/7/decision/extra");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.POST, "/api/review/7%2F8/decision"))
                .isEqualTo("/api/v1/review/7%2F8/decision");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/internal/api/search"))
                .isEqualTo("/internal/api/search");
        assertThat(ApiCompatibilityWebFilter.normalizePath(HttpMethod.GET, "/public/api/search"))
                .isEqualTo("/public/api/search");
    }

    @Test
    void keepsAdminAliasProtectedByTheExistingAuthenticationBoundary() {
        var authority = mock(com.platform.gateway.session.SessionAuthorityClient.class);
        when(authority.internalToken()).thenReturn("test-internal");
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayJwtHelper jwtHelper = mock(GatewayJwtHelper.class);
        GatewayAuthFilter authFilter = new GatewayAuthFilter(authority, new com.platform.gateway.session.ClientIpResolver(""), new ObjectMapper());
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/admin/articles/1");
        WebFilterChain chain = authChain(authFilter, current -> {
            downstreamCalled.set(true);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(downstreamCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).hasToString("401 UNAUTHORIZED");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":401");
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void keepsReviewAliasProtectedByTheExistingRoleBoundary() {
        var authority = mock(com.platform.gateway.session.SessionAuthorityClient.class);
        when(authority.internalToken()).thenReturn("test-internal");
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        GatewayJwtHelper jwtHelper = mock(GatewayJwtHelper.class);
        when(redisTemplate.hasKey("jwt:blacklist:user-token")).thenReturn(Mono.just(false));
        when(jwtHelper.parse("user-token")).thenReturn(JwtUser.builder()
                .userId(9L).username("bob").role("USER").build());
        when(jwtHelper.shouldRefresh("user-token")).thenReturn(false);
        when(authority.validate("user-token")).thenReturn(Mono.just(new com.platform.gateway.session.SessionAuthorityClient.Identity(9L,"bob","USER")));
        GatewayAuthFilter authFilter = new GatewayAuthFilter(authority, new com.platform.gateway.session.ClientIpResolver(""), new ObjectMapper());
        AtomicBoolean downstreamCalled = new AtomicBoolean();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/review/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build());
        WebFilterChain chain = authChain(authFilter, current -> {
            downstreamCalled.set(true);
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(downstreamCalled).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).hasToString("403 FORBIDDEN");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":403");
    }

    private MockServerWebExchange exchange(HttpMethod method, String uri) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, URI.create(uri)).build());
    }

    private WebFilterChain authChain(GatewayAuthFilter authFilter, GatewayFilterChain downstream) {
        return exchange -> authFilter.filter(exchange, downstream);
    }
}
