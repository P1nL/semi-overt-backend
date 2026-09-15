package com.platform.content.service;

import com.platform.content.api.req.ArticlePolishReq;
import com.platform.content.api.req.ArticlePolishReq.Segment;
import com.platform.content.api.resp.ArticlePolishResp;
import com.platform.content.service.ai.ArticlePolishClient;
import com.platform.kernel.exception.BusinessException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Generates an ephemeral suggestion; never saves an article or changes its version. */
@Service
@RequiredArgsConstructor
public class ArticlePolishService {
    public static final int MAX_INPUT_CHARS = 12000;
    private final ArticlePolishClient client;
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')
            if count >= 6 then return -1 end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 60) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    public ArticlePolishResp polish(Long userId, ArticlePolishReq request) {
        if (userId == null) throw BusinessException.unauthorized("请先登录后再使用 AI 润色");
        validateInput(request);
        client.checkConfigured();
        String key = "content:ai-polish:{" + userId + "}";
        String lease = UUID.randomUUID().toString();
        Long acquired;
        try {
            acquired = redis.execute(ACQUIRE, List.of(key + ":active", key + ":rate"),
                    lease, String.valueOf((client.timeoutSeconds() + 10L) * 1000));
        } catch (RuntimeException e) {
            throw new BusinessException(503, "AI 润色暂时不可用，请稍后重试");
        }
        if (acquired == null) throw new BusinessException(503, "AI 润色暂时不可用，请稍后重试");
        if (acquired != 1L) {
            throw BusinessException.tooManyRequests(acquired == 0L ? "上一次润色仍在处理中，请稍后重试" : "润色请求过于频繁，请稍后重试",
                    Map.of("retryAfterSeconds", acquired == 0L ? 5 : 60));
        }
        try {
            var response = client.generate(request);
            validateOutput(request, response);
            return response;
        } finally {
            try { redis.execute(RELEASE, List.of(key + ":active"), lease); }
            catch (RuntimeException ignored) { /* Bounded lease expires even during a Redis outage. */ }
        }
    }

    static void validateInput(ArticlePolishReq request) {
        if (request == null || request.segments() == null || request.segments().isEmpty() || request.segments().size() > 200)
            throw BusinessException.badRequest("请提供需要润色的正文，最多支持 200 个文本片段");
        var ids = new HashSet<String>();
        long chars = 0;
        for (Segment segment : request.segments()) {
            if (segment == null || segment.id() == null || segment.id().length() > 128
                    || !segment.id().matches("[0-9]+(?:\\.[0-9]+)*") || !ids.add(segment.id())
                    || segment.text() == null || segment.text().isBlank())
                throw BusinessException.badRequest("润色内容格式不正确");
            chars += segment.text().length();
        }
        if (chars > MAX_INPUT_CHARS) throw BusinessException.badRequest("单次润色正文不能超过 12000 字符");
    }

    static void validateOutput(ArticlePolishReq request, ArticlePolishResp response) {
        if (response == null || response.segments() == null || response.segments().size() != request.segments().size())
            throw invalidResponse();
        var expected = new HashSet<String>();
        request.segments().forEach(segment -> expected.add(segment.id()));
        long chars = 0;
        for (Segment segment : response.segments()) {
            if (segment == null || !expected.remove(segment.id()) || segment.text() == null
                    || segment.text().isBlank() || segment.text().length() > 24000) throw invalidResponse();
            chars += segment.text().length();
        }
        if (!expected.isEmpty() || chars > 36000) throw invalidResponse();
    }

    private static BusinessException invalidResponse() {
        return new BusinessException(502, "AI 返回的润色内容不完整，请重新生成");
    }
}
