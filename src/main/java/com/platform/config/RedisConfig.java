package com.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 * 本项目 Redis 存储的值均为简单字符串，直接使用 StringRedisTemplate
 * 全局 ObjectMapper 支持 Java 8 时间类型（LocalDateTime）
 */
@Configuration
public class RedisConfig {

    /**
     * 使用 StringRedisTemplate，key/value 均为 String
     * 适合本项目场景：Token 黑名单、草稿缓存、重置密码 Token、限流计数
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * 全局 ObjectMapper，支持 LocalDateTime 序列化
     * 注入到 SecurityConfig 等需要 JSON 序列化的地方
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持 Java 8 时间类型
        mapper.registerModule(new JavaTimeModule());
        // 时间序列化为 ISO 8601 字符串（非时间戳）
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}