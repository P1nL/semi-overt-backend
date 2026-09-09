package com.platform.gateway.session;
import com.platform.gateway.filter.*;
import com.platform.gateway.config.GatewayRouteConfig;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.filter.ratelimit.*;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.*;
import java.util.*;
/** Manual loopback acceptance gateway; real production routes and auth, synthetic discovery/Redis. */
@SpringBootConfiguration @EnableAutoConfiguration
@Import({GatewayRouteConfig.class,ApiCompatibilityWebFilter.class,GatewayAuthFilter.class,ClientIpResolver.class})
public class GatewayBrowserHarness {
    @Bean SessionAuthorityClient authority(){return new SessionAuthorityClient(WebClient.builder(),"http://127.0.0.1:18081","s2-test-only-internal");}
    @Bean KeyResolver clientRateLimiterKeyResolver(){return e->Mono.just("acceptance");}
    @Bean RedisRateLimiter defaultRedisRateLimiter(){
        var limiter=org.mockito.Mockito.mock(RedisRateLimiter.class);
        org.mockito.Mockito.when(limiter.isAllowed(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(new RateLimiter.Response(true,Map.of())));
        return limiter;
    }
    @Bean ServiceInstanceListSupplier instances(){return new ServiceInstanceListSupplier(){
        public String getServiceId(){return "auth-service";}
        public Flux<List<org.springframework.cloud.client.ServiceInstance>> get(){return Flux.just(List.of(new DefaultServiceInstance("auth-1","auth-service","127.0.0.1",18081,false)));}
    };}
    public static void main(String[] args){
        var app=new SpringApplication(GatewayBrowserHarness.class);app.setDefaultProperties(Map.of(
            "spring.config.location","optional:classpath:/s2-empty.yml","spring.main.web-application-type","reactive",
            "spring.cloud.nacos.config.enabled",false,"spring.cloud.nacos.config.import-check.enabled",false,"spring.cloud.nacos.discovery.enabled",false,
            "server.port",18080,"server.address","127.0.0.1"));app.run();
    }
}
