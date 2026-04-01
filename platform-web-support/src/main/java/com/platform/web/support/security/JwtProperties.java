package com.platform.web.support.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性，集中维护令牌签发与校验所需参数。
 */

@Data
@ConfigurationProperties(prefix = "jwt.token")
public class JwtProperties {
    private long expiration;
    private long rememberMeExpiration;
    private String signKey;
    private long refreshThreshold;
}
