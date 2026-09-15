package com.platform.content.service.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.platform.kernel.exception.BusinessException;
import dev.langchain4j.exception.HttpException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class ArticlePolishFailureTest {
    @Test void handshakeFailureIsNotMisreportedAsIncompleteModelContent() {
        var error = ArticlePolishClient.mapFailure(new IllegalStateException(
                "secret request", new SSLHandshakeException("secret provider detail")));
        assertEquals(502, error.getCode());
        assertEquals("DeepSeek 安全连接失败，请检查后端网络或代理后重试", error.getMessage());
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("内容不完整"));
    }

    @Test void connectionAndDnsFailuresHaveActionableSanitizedMessages() {
        for (var cause : new Exception[]{new ConnectException("secret proxy"), new UnknownHostException("secret host")}) {
            var error = ArticlePolishClient.mapFailure(new IllegalStateException(cause));
            assertEquals(502, error.getCode());
            assertEquals("无法连接 DeepSeek，请检查后端网络、DNS 或代理后重试", error.getMessage());
        }
    }

    @Test void existingTimeoutInterruptionAndRateLimitMappingsRemainIntact() {
        assertEquals(504, ArticlePolishClient.mapFailure(new IllegalStateException(new HttpTimeoutException("secret"))).getCode());
        assertEquals(504, ArticlePolishClient.mapFailure(new BoundedLangChainHttpClient.RequestTimeoutException()).getCode());
        assertEquals(503, ArticlePolishClient.mapFailure(new BoundedLangChainHttpClient.RequestInterruptedException()).getCode());
        assertEquals(429, ArticlePolishClient.mapFailure(new HttpException(429, "secret provider body")).getCode());
    }

    @Test void businessFailureIsPreservedAndUnknownFailureDoesNotClaimInvalidContent() {
        var original = new BusinessException(503, "local failure");
        assertSame(original, ArticlePolishClient.mapFailure(new IllegalStateException(original)));
        var error = ArticlePolishClient.mapFailure(new IllegalStateException("secret provider body"));
        assertEquals(502, error.getCode());
        assertEquals("DeepSeek 模型请求失败，请稍后重试", error.getMessage());
    }
}
