package com.platform.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt.token")
public class JwtProperties {
    private long expiration;
    private long rememberMeExpiration;
    private String signKey;
    private long refreshThreshold;
}
