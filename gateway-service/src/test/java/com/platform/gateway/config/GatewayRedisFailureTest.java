package com.platform.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GatewayRedisFailureTest {
    ReactiveStringRedisTemplate redis;
    RedisRateLimiter limiter;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        redis=mock(ReactiveStringRedisTemplate.class);
        var context=mock(ApplicationContext.class);
        when(context.getBean(ReactiveStringRedisTemplate.class)).thenReturn(redis);
        when(context.getBean("redisRequestRateLimiterScript",RedisScript.class)).thenReturn(mock(RedisScript.class));
        when(context.getBeanNamesForType(ConfigurationService.class)).thenReturn(new String[0]);
        limiter=new GatewayRateLimitConfig().defaultRedisRateLimiter(30,60,1);
        limiter.setApplicationContext(context);
    }
    @Test void redisFailureMustNotBeConvertedIntoPermissionToProceed() {
        when(redis.execute(any(RedisScript.class),anyList(),anyList())).thenReturn(Flux.error(new IllegalStateException("synthetic Redis outage")));
        StepVerifier.create(limiter.isAllowed("content","ip:test"))
                .expectErrorSatisfies(error -> {assertInstanceOf(ResponseStatusException.class,error);assertEquals(503,((ResponseStatusException)error).getStatusCode().value());})
                .verify();
    }
    @Test void ordinaryAllowAndRateRejectionArePreserved() {
        when(redis.execute(any(RedisScript.class),anyList(),anyList())).thenReturn(Flux.just(List.of(1L,59L)),Flux.just(List.of(0L,0L)));
        StepVerifier.create(limiter.isAllowed("content","ip:test")).assertNext(r->assertTrue(r.isAllowed())).verifyComplete();
        StepVerifier.create(limiter.isAllowed("content","ip:test")).assertNext(r->assertFalse(r.isAllowed())).verifyComplete();
    }
    @Test void redisBlackholeHasBoundedServiceFailure() {
        when(redis.execute(any(RedisScript.class),anyList(),anyList())).thenReturn(Flux.never());
        StepVerifier.withVirtualTime(()->limiter.isAllowed("content","ip:test"))
                .thenAwait(java.time.Duration.ofSeconds(4))
                .expectErrorSatisfies(e->assertEquals(503,((ResponseStatusException)e).getStatusCode().value())).verify();
    }
    @Test void missingQuotaMetadataCannotBecomeAnAllowedDecision() {
        limiter.setIncludeHeaders(false);
        when(redis.execute(any(RedisScript.class),anyList(),anyList())).thenReturn(Flux.just(List.of(1L,59L)));
        StepVerifier.create(limiter.isAllowed("content","ip:test")).expectError(ResponseStatusException.class).verify();
    }
}
