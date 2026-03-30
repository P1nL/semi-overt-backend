package com.platform.gateway.security;

import com.platform.common.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * 网关侧 JWT 辅助类。
 * 负责解析 token、判断剩余有效期以及在需要时重新签发刷新 token。
 */
@Component
public class GatewayJwtHelper {

    private final JwtProperties jwtProperties;

    public GatewayJwtHelper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 解析 token 中的最小用户身份信息。
     * 解析失败或 token 已过期时返回 null。
     */
    public JwtUser parse(String token) {
        try {
            Claims claims = parseClaims(token);
            String role = claims.get("role", String.class);
            return JwtUser.builder()
                    .userId(Long.valueOf(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .role((role == null || role.isBlank()) ? "USER" : role)
                    .build();
        } catch (ExpiredJwtException | SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 计算 token 剩余有效期，单位毫秒。
     */
    public long getRemainingMillis(String token) {
        try {
            Date expireAt = parseClaims(token).getExpiration();
            return Math.max(expireAt.getTime() - System.currentTimeMillis(), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 判断 token 是否进入刷新阈值窗口。
     */
    public boolean shouldRefresh(String token) {
        long remainingMinutes = getRemainingMillis(token) / 1000 / 60;
        return remainingMinutes > 0 && remainingMinutes < jwtProperties.getRefreshThreshold();
    }

    /**
     * 重新签发 token。
     */
    public String createToken(Long userId, String username, String role, boolean rememberMe) {
        long ttlMinutes = rememberMe ? jwtProperties.getRememberMeExpiration() : jwtProperties.getExpiration();
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + ttlMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT Claims。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取签名密钥。
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSignKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 网关侧最小用户身份载荷。
     */
    @Value
    @Builder
    public static class JwtUser {
        Long userId;
        String username;
        String role;
    }
}
