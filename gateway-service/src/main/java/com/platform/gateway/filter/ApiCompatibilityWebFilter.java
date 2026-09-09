package com.platform.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalizes the approved legacy API aliases before Gateway route matching. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiCompatibilityWebFilter implements WebFilter, Ordered {

    private static final String API_PREFIX = "/api";
    private static final String VERSIONED_API_PREFIX = "/api/v1";
    private static final Pattern REVIEW_DECISION = Pattern.compile("^/api/v1/review/([0-9]+)/decision$");
    private static final Pattern REVIEW_LOGS = Pattern.compile("^/api/v1/review/([0-9]+)/logs$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        String normalizedPath = normalizePath(exchange.getRequest().getMethod(), rawPath);
        if (rawPath.equals(normalizedPath)) {
            return chain.filter(exchange);
        }

        URI normalizedUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replacePath(normalizedPath)
                .build(true)
                .toUri();
        ServerHttpRequest normalizedRequest = exchange.getRequest().mutate()
                .uri(normalizedUri)
                .build();
        return chain.filter(exchange.mutate().request(normalizedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    static String normalizePath(HttpMethod method, String rawPath) {
        if (rawPath == null || !isApiPath(rawPath)) {
            return rawPath;
        }

        String normalizedPath = normalizeApiPrefix(rawPath);
        if (method == HttpMethod.POST) {
            Matcher decision = REVIEW_DECISION.matcher(normalizedPath);
            if (decision.matches()) {
                return "/api/v1/reviews/" + decision.group(1) + "/action";
            }
            if ("/api/v1/upload".equals(normalizedPath)) {
                return "/api/v1/uploads/images";
            }
        }
        if (method == HttpMethod.GET) {
            if ("/api/v1/review/pending".equals(normalizedPath)) {
                return "/api/v1/reviews/pending";
            }
            Matcher logs = REVIEW_LOGS.matcher(normalizedPath);
            if (logs.matches()) {
                return "/api/v1/reviews/" + logs.group(1) + "/logs";
            }
            if ("/api/v1/search".equals(normalizedPath)) {
                return "/api/v1/search/articles";
            }
        }
        if (method == HttpMethod.PUT && "/api/v1/users/me".equals(normalizedPath)) {
            return "/api/v1/users/me/profile";
        }
        return normalizedPath;
    }

    private static boolean isApiPath(String rawPath) {
        return API_PREFIX.equals(rawPath) || rawPath.startsWith(API_PREFIX + "/");
    }

    private static String normalizeApiPrefix(String rawPath) {
        if (VERSIONED_API_PREFIX.equals(rawPath) || rawPath.startsWith(VERSIONED_API_PREFIX + "/")) {
            return rawPath;
        }
        if (API_PREFIX.equals(rawPath)) {
            return VERSIONED_API_PREFIX;
        }
        return VERSIONED_API_PREFIX + rawPath.substring(API_PREFIX.length());
    }
}