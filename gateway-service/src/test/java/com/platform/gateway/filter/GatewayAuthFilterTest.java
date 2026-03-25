package com.platform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.HeaderNames;
import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.gateway.security.GatewayJwtHelper.JwtUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAuthFilterTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private GatewayJwtHelper jwtHelper;
    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        jwtHelper = mock(GatewayJwtHelper.class);
        filter = new GatewayAuthFilter(redisTemplate, jwtHelper, new ObjectMapper());
    }

    @Test
    void whitelistedRequestWithoutTokenStaysAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/articles/12").build()
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainCapturing(forwardedRequest);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwardedRequest.get()).isNotNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HeaderNames.X_USER_ID)).isNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HeaderNames.X_TRACE_ID)).isNotBlank();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void whitelistedRequestWithValidTokenInjectsUserHeaders() {
        when(redisTemplate.hasKey("jwt:blacklist:valid-token")).thenReturn(Mono.just(false));
        when(jwtHelper.parse("valid-token")).thenReturn(JwtUser.builder()
                .userId(7L)
                .username("alice")
                .role("USER")
                .build());
        when(jwtHelper.shouldRefresh("valid-token")).thenReturn(false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/articles/12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build()
        );
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = chainCapturing(forwardedRequest);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwardedRequest.get()).isNotNull();
        assertThat(forwardedRequest.get().getHeaders().getFirst(HeaderNames.X_USER_ID)).isEqualTo("7");
        assertThat(forwardedRequest.get().getHeaders().getFirst(HeaderNames.X_USERNAME)).isEqualTo("alice");
        assertThat(forwardedRequest.get().getHeaders().getFirst(HeaderNames.X_USER_ROLE)).isEqualTo("USER");
    }

    @Test
    void whitelistedRequestWithBlacklistedTokenReturnsUnauthorized() {
        when(redisTemplate.hasKey("jwt:blacklist:revoked-token")).thenReturn(Mono.just(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/articles/12")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer revoked-token")
                        .build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).hasToString("200 OK");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":401");
    }

    @Test
    void protectedRequestWithoutTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/articles/drafts").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":401");
    }

    @Test
    void reviewRequestForNonAdminReturnsForbidden() {
        when(redisTemplate.hasKey("jwt:blacklist:user-token")).thenReturn(Mono.just(false));
        when(jwtHelper.parse("user-token")).thenReturn(JwtUser.builder()
                .userId(9L)
                .username("bob")
                .role("USER")
                .build());
        when(jwtHelper.shouldRefresh("user-token")).thenReturn(false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/reviews/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = currentExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled.get()).isFalse();
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":403");
    }

    private GatewayFilterChain chainCapturing(AtomicReference<ServerHttpRequest> forwardedRequest) {
        return exchange -> {
            forwardedRequest.set(exchange.getRequest());
            return Mono.empty();
        };
    }
}
