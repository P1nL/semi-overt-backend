package com.platform.config;

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

}
