package com.platform.content.service;

import com.platform.content.api.req.ArticlePolishReq;
import com.platform.content.api.req.ArticlePolishReq.Segment;
import com.platform.content.api.resp.ArticlePolishResp;
import com.platform.content.service.ai.ArticlePolishClient;
import com.platform.kernel.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticlePolishServiceTest {
    private final ArticlePolishClient client = mock(ArticlePolishClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ArticlePolishService service = new ArticlePolishService(client, redis);
    private final ArticlePolishReq input = new ArticlePolishReq(List.of(new Segment("0.0", "原文内容")));
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(client.timeoutSeconds()).thenReturn(45);
        when(client.generate(input)).thenReturn(new ArticlePolishResp(List.of(new Segment("0.0", "润色内容"))));
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void returnsOnlyEphemeralSuggestion() {
        assertEquals("润色内容", service.polish(12L, input).segments().get(0).text());
        verify(client).generate(input);
    }
    @Test void unauthenticatedAndInvalidInputNeverCallProvider() {
        assertEquals(401, assertThrows(BusinessException.class, () -> service.polish(null, input)).getCode());
        for (var invalid : List.of(new ArticlePolishReq(List.of()),
                new ArticlePolishReq(List.of(new Segment("0.0", "a"), new Segment("0.0", "b"))),
                new ArticlePolishReq(List.of(new Segment("__proto__", "a"))),
                new ArticlePolishReq(List.of(new Segment("0.0", "x".repeat(12001)))))) {
            assertEquals(400, assertThrows(BusinessException.class, () -> service.polish(12L, invalid)).getCode());
        }
        verifyNoInteractions(client, redis);
    }
    @Test void unconfiguredProviderNeverConsumesQuota() {
        doThrow(new BusinessException(503, "not configured")).when(client).checkConfigured();
        assertEquals(503, assertThrows(BusinessException.class, () -> service.polish(12L, input)).getCode());
        verifyNoInteractions(redis);
    }
    @Test @SuppressWarnings("unchecked") void rateLimitAndConcurrencyFailClosed() {
        for (long result : new long[]{0L, -1L}) {
            doReturn(result).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
            assertEquals(429, assertThrows(BusinessException.class, () -> service.polish(12L, input)).getCode());
        }
        verify(client, never()).generate(any());
    }
    @Test @SuppressWarnings("unchecked") void redisOutageDoesNotFallBackToUnlimitedPaidCalls() {
        doThrow(new IllegalStateException("redis unavailable")).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertEquals(503, assertThrows(BusinessException.class, () -> service.polish(12L, input)).getCode());
        verify(client, never()).generate(any());
    }
    @Test @SuppressWarnings("unchecked") void releasesLeaseAfterProviderFailure() {
        when(client.generate(input)).thenThrow(new BusinessException(504, "timeout"));
        assertEquals(504, assertThrows(BusinessException.class, () -> service.polish(12L, input)).getCode());
        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void rejectsMissingUnknownDuplicateAndBlankOutput() {
        var two = new ArticlePolishReq(List.of(new Segment("0.0", "one"), new Segment("1.0", "two")));
        for (var output : List.of(new ArticlePolishResp(List.of()),
                new ArticlePolishResp(List.of(new Segment("0.0", "a"), new Segment("0.0", "b"))),
                new ArticlePolishResp(List.of(new Segment("0.0", "a"), new Segment("9.0", "b"))),
                new ArticlePolishResp(List.of(new Segment("0.0", "a"), new Segment("1.0", " "))))) {
            assertEquals(502, assertThrows(BusinessException.class, () -> ArticlePolishService.validateOutput(two, output)).getCode());
        }
    }
    @Test void acceptsReorderedSegmentsAndLeavesStructureToEditor() {
        var two = new ArticlePolishReq(List.of(new Segment("0.0", "one"), new Segment("1.0", "two")));
        assertDoesNotThrow(() -> ArticlePolishService.validateOutput(two,
                new ArticlePolishResp(List.of(new Segment("1.0", "second"), new Segment("0.0", "first")))));
    }
}
