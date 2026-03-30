package com.platform.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.constant.HeaderNames;
import com.platform.gateway.security.GatewayJwtHelper;
import com.platform.gateway.security.GatewayJwtHelper.JwtUser;
import com.platform.util.Result;
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

/**
 * 网关全局鉴权过滤器。
 * 负责拦截公网请求、清洗伪造头、补齐 TraceId、校验 JWT、透传用户身份头，并对公共路由放行。
 */
@Component
@RequiredArgsConstructor
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ReactiveStringRedisTemplate redisTemplate;
    private final GatewayJwtHelper jwtHelper;
    private final ObjectMapper objectMapper;

    /**
     * 执行网关统一鉴权。
     * 顺序为：拦截内部接口 -> 生成 TraceId -> 清洗头部 -> 认证 -> 白名单放行或权限拦截。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/internal/")) {
            return writeResult(exchange, HttpStatus.NOT_FOUND, Result.notFound("资源不存在"));
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
                                Result.unauthorized("未登录或 Token 已失效"));
                    }

                    if (path.startsWith("/api/v1/reviews/")
                            && !"ADMIN".equalsIgnoreCase(authContext.jwtUser().getRole())) {
                        return writeResult(baseExchange, HttpStatus.FORBIDDEN,
                                Result.forbidden("权限不足"));
                    }

                    return chain.filter(authContext.exchange());
                });
    }

    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 处理白名单请求。
     * 白名单接口允许匿名访问，但若显式携带了非法 token，仍返回 401。
     */
    private Mono<Void> handleWhitelistedRequest(AuthContext authContext, GatewayFilterChain chain) {
        if (authContext.status() == AuthStatus.INVALID_TOKEN) {
            return writeResult(authContext.exchange(), HttpStatus.UNAUTHORIZED,
                    Result.unauthorized("未登录或 Token 已失效"));
        }
        return chain.filter(authContext.exchange());
    }

    /**
     * 解析或生成 TraceId。
     */
    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(HeaderNames.X_TRACE_ID);
        return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
    }

    /**
     * 清洗客户端传入的身份头，只保留网关自己生成的 TraceId。
     */
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

    /**
     * 判断当前请求是否属于公共白名单。
     */
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

    /**
     * 从 Authorization 头中提取 Bearer token。
     */
    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 执行 token 校验与身份头透传。
     * 若 token 命中黑名单或解析失败，返回 INVALID_TOKEN；若接近过期，则顺带回写刷新后的 token。
     */
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

    /**
     * 输出统一 JSON 错误响应。
     */
    private Mono<Void> writeResult(ServerWebExchange exchange, HttpStatus httpStatus, Result<?> result) {
        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(toJson(result).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 序列化统一响应对象。
     */
    private String toJson(Result<?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"code\":500,\"message\":\"serialize error\",\"data\":null}";
        }
    }

    /**
     * 网关认证状态枚举。
     */
    private enum AuthStatus {
        NO_TOKEN,
        INVALID_TOKEN,
        AUTHENTICATED
    }

    /**
     * 鉴权结果上下文。
     */
    private record AuthContext(ServerWebExchange exchange, AuthStatus status, JwtUser jwtUser) {
    }
}
