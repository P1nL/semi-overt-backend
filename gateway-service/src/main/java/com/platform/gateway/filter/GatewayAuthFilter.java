package com.platform.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.gateway.security.GatewayJwtHelper.JwtUser;
import com.platform.kernel.constant.HeaderNames;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayJwtHelper jwtHelper;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/internal/")) {
            return writeResult(exchange, HttpStatus.NOT_FOUND, Result.notFound("Not found"));
        }

        String traceId = resolveTraceId(exchange.getRequest());
        ServerHttpRequest baseRequest = sanitizeHeaders(exchange.getRequest(), traceId);
        ServerWebExchange baseExchange = exchange.mutate().request(baseRequest).build();

        return authenticate(baseExchange, baseRequest)
                .flatMap(authContext -> {
                    if (isWhitelisted(exchange.getRequest().getMethod(), path)) {
                        return handleWhitelistedRequest(authContext, chain);
                    }

                    if (authContext.status() != AuthStatus.AUTHENTICATED) {
                        return writeResult(baseExchange, HttpStatus.UNAUTHORIZED,
                                Result.unauthorized("Authentication required or token is invalid"));
                    }

                    if (path.startsWith("/api/v1/reviews/")
                            && !"ADMIN".equalsIgnoreCase(authContext.jwtUser().getRole())) {
                        return writeResult(baseExchange, HttpStatus.FORBIDDEN,
                                Result.forbidden("没有审核权限"));
                    }

                    if (path.startsWith("/api/v1/admin/")
                            && !"ADMIN".equalsIgnoreCase(authContext.jwtUser().getRole())) {
                        return writeResult(baseExchange, HttpStatus.FORBIDDEN,
                                Result.forbidden("需要管理员权限"));
                    }

                    return chain.filter(authContext.exchange());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private Mono<Void> handleWhitelistedRequest(AuthContext authContext, GatewayFilterChain chain) {
        // 白名单路径对无效/过期 token 容错：当作未登录放行，不返回 401
        // 这样带着过期 token 的用户仍能正常浏览公开内容
        return chain.filter(authContext.exchange());
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(HeaderNames.X_TRACE_ID);
        return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
    }

    private ServerHttpRequest sanitizeHeaders(ServerHttpRequest request, String traceId) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(HeaderNames.X_USER_ID);
                    headers.remove(HeaderNames.X_USERNAME);
                    headers.remove(HeaderNames.X_USER_ROLE);
                    headers.remove(HeaderNames.X_TRACE_ID);
                    headers.add(HeaderNames.X_TRACE_ID, traceId);
                })
                .build();
    }

    private boolean isWhitelisted(HttpMethod method, String path) {
        if (path.startsWith("/static/uploads/")) {
            return true;
        }
        if (method == HttpMethod.POST && (
                "/api/v1/auth/register".equals(path)
                        || "/api/v1/auth/login".equals(path)
                        || "/api/v1/auth/forgot-password".equals(path)
                        || "/api/v1/auth/reset-password".equals(path))) {
            return true;
        }
        if (method == HttpMethod.GET && (
                "/api/v1/home".equals(path)
                        || PATH_MATCHER.match("/api/v1/categories/**", path)
                        || PATH_MATCHER.match("/api/v1/search/**", path)
                        || path.matches("/api/v1/articles/\\d+")
                        || path.matches("/api/v1/users/[^/]+/profile")
                        || path.matches("/api/v1/reviews/\\d+/logs"))) {
            return true;
        }
        return false;
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private Mono<AuthContext> authenticate(ServerWebExchange baseExchange, ServerHttpRequest baseRequest) {
        String token = extractToken(baseRequest);
        if (token == null || token.isBlank()) {
            return Mono.just(new AuthContext(baseExchange, AuthStatus.NO_TOKEN, null));
        }

        return redisTemplate.hasKey("jwt:blacklist:" + token)
                .map(Boolean.TRUE::equals)
                .defaultIfEmpty(false)
                .map(blacklisted -> {
                    if (blacklisted) {
                        return new AuthContext(baseExchange, AuthStatus.INVALID_TOKEN, null);
                    }

                    JwtUser jwtUser;
                    try {
                        jwtUser = jwtHelper.parse(token);
                    } catch (RuntimeException ex) {
                        return new AuthContext(baseExchange, AuthStatus.INVALID_TOKEN, null);
                    }
                    if (jwtUser == null) {
                        return new AuthContext(baseExchange, AuthStatus.INVALID_TOKEN, null);
                    }

                    ServerHttpRequest authedRequest = baseRequest.mutate()
                            .header(HeaderNames.X_USER_ID, String.valueOf(jwtUser.getUserId()))
                            .header(HeaderNames.X_USERNAME, jwtUser.getUsername())
                            .header(HeaderNames.X_USER_ROLE, jwtUser.getRole())
                            .build();

                    if (jwtHelper.shouldRefresh(token)) {
                        String newToken = jwtHelper.createToken(
                                jwtUser.getUserId(),
                                jwtUser.getUsername(),
                                jwtUser.getRole(),
                                false
                        );
                        baseExchange.getResponse().getHeaders().set("New-Token", newToken);
                    }

                    return new AuthContext(
                            baseExchange.mutate().request(authedRequest).build(),
                            AuthStatus.AUTHENTICATED,
                            jwtUser
                    );
                });
    }

    private Mono<Void> writeResult(ServerWebExchange exchange, HttpStatus httpStatus, Result<?> result) {
        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(toJson(result).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String toJson(Result<?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"code\":500,\"message\":\"serialize error\",\"data\":null}";
        }
    }

    private enum AuthStatus {
        NO_TOKEN,
        INVALID_TOKEN,
        AUTHENTICATED
    }

    private record AuthContext(ServerWebExchange exchange, AuthStatus status, JwtUser jwtUser) {
    }
}
