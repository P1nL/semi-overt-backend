package com.platform.gateway.filter;

import com.platform.gateway.config.GatewayRouteConfig;
import com.platform.gateway.security.GatewayJwtHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=GatewayRoutingHttpTest.App.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.config.location=optional:classpath:/s1-empty.yml","spring.main.web-application-type=reactive",
 "spring.cloud.nacos.config.enabled=false","spring.cloud.nacos.config.import-check.enabled=false",
 "spring.cloud.nacos.discovery.enabled=false","spring.cloud.discovery.enabled=false"})
class GatewayRoutingHttpTest {
    @LocalServerPort int port;
    @MockBean RedisRateLimiter limiter;
    @MockBean ReactiveStringRedisTemplate redis;
    @MockBean GatewayJwtHelper jwt;
    @MockBean com.platform.gateway.session.SessionAuthorityClient authority;
    @SpringBootConfiguration @EnableAutoConfiguration
    @Import({GatewayRouteConfig.class,ApiCompatibilityWebFilter.class,GatewayAuthFilter.class,com.platform.gateway.session.ClientIpResolver.class})
    static class App {
        @Bean KeyResolver clientRateLimiterKeyResolver(){return e->Mono.just("synthetic");}
        @Bean Capture capture(){return new Capture();}
    }
    // Stops before network/rate-limit execution; production route matching and auth are real.
    static class Capture implements GlobalFilter,Ordered {
        public int getOrder(){return -90;}
        public Mono<Void> filter(org.springframework.web.server.ServerWebExchange e,org.springframework.cloud.gateway.filter.GatewayFilterChain chain){
            Route route=e.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String body=route.getId()+"|"+e.getRequest().getURI().getRawPath()+"|"+e.getRequest().getURI().getRawQuery();
            return e.getResponse().writeWith(Mono.just(e.getResponse().bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
    }
    @Test void productionRoutePredicatesMatchBothPrefixesAndKeepAuth() {
        org.mockito.Mockito.when(authority.internalToken()).thenReturn("test-internal");
        org.mockito.Mockito.when(authority.consume(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq("SEARCH"))).thenReturn(Mono.just(new com.platform.gateway.session.SessionAuthorityClient.Budget(true,0)));
        for(String prefix:new String[]{"/api","/api/v1"}) {
            String result=HttpClient.create().get().uri("http://127.0.0.1:"+port+prefix+"/search?keyword=a%2Fb")
                .responseSingle((r,b)->{assertEquals(200,r.status().code());return b.asString();}).block(Duration.ofSeconds(10));
            assertEquals("search-service|/api/v1/search/articles|keyword=a%2Fb",result);
            HttpClient.create().get().uri("http://127.0.0.1:"+port+prefix+"/review/pending")
                .responseSingle((r,b)->{assertEquals(401,r.status().code());return b.asString();}).block(Duration.ofSeconds(10));
        }
    }
}
