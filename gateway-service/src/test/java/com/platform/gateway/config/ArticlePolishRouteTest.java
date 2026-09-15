package com.platform.gateway.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestSizeGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

class ArticlePolishRouteTest {
    @Test void onlyPolishPostGetsLongTimeoutAndItPrecedesGeneralContentRoute() {
        var limiter = mock(RedisRateLimiter.class);
        KeyResolver key = exchange -> Mono.just("test-user");
        try (var context = new GenericApplicationContext()) {
            context.registerBean(PathRoutePredicateFactory.class, PathRoutePredicateFactory::new);
            context.registerBean(MethodRoutePredicateFactory.class, MethodRoutePredicateFactory::new);
            context.registerBean(RequestRateLimiterGatewayFilterFactory.class, () -> new RequestRateLimiterGatewayFilterFactory(limiter, key));
            context.registerBean(RequestSizeGatewayFilterFactory.class, RequestSizeGatewayFilterFactory::new);
            context.refresh();
            var routes = new GatewayRouteConfig().routeLocator(new RouteLocatorBuilder(context), limiter, key)
                    .getRoutes().collectList().block();
            assertNotNull(routes);
            var polish = routes.stream().filter(route -> route.getId().equals("content-service-ai-polish")).findFirst().orElseThrow();
            assertEquals(95000, polish.getMetadata().get(RESPONSE_TIMEOUT_ATTR));
            assertEquals("lb://content-service", polish.getUri().toString());
            assertEquals(2, polish.getFilters().size());
            for (var route : routes) if (route != polish) assertFalse(route.getMetadata().containsKey(RESPONSE_TIMEOUT_ATTR));
            var post = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/articles/ai-polish"));
            assertTrue(Mono.from(polish.getPredicate().apply(post)).block());
            assertFalse(Mono.from(polish.getPredicate().apply(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/articles/ai-polish")))).block());
            assertFalse(Mono.from(polish.getPredicate().apply(MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/articles/12/submit")))).block());
            assertTrue(routes.indexOf(polish) < routes.indexOf(routes.stream().filter(route -> route.getId().equals("content-service")).findFirst().orElseThrow()));
        }
    }
}
