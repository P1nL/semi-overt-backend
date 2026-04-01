package com.platform.gateway.security;

import com.platform.web.support.security.JwtProperties;
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
 * 缃戝叧渚?JWT 杈呭姪绫汇€?
 * 璐熻矗瑙ｆ瀽 token銆佸垽鏂墿浣欐湁鏁堟湡浠ュ強鍦ㄩ渶瑕佹椂閲嶆柊绛惧彂鍒锋柊 token銆?
 */
@Component
public class GatewayJwtHelper {

    private final JwtProperties jwtProperties;

    public GatewayJwtHelper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 瑙ｆ瀽 token 涓殑鏈€灏忕敤鎴疯韩浠戒俊鎭€?
     * 瑙ｆ瀽澶辫触鎴?token 宸茶繃鏈熸椂杩斿洖 null銆?
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
     * 璁＄畻 token 鍓╀綑鏈夋晥鏈燂紝鍗曚綅姣銆?
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
     * 鍒ゆ柇 token 鏄惁杩涘叆鍒锋柊闃堝€肩獥鍙ｃ€?
     */
    public boolean shouldRefresh(String token) {
        long remainingMinutes = getRemainingMillis(token) / 1000 / 60;
        return remainingMinutes > 0 && remainingMinutes < jwtProperties.getRefreshThreshold();
    }

    /**
     * 閲嶆柊绛惧彂 token銆?
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
     * 瑙ｆ瀽 JWT Claims銆?
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 鑾峰彇绛惧悕瀵嗛挜銆?
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSignKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 缃戝叧渚ф渶灏忕敤鎴疯韩浠借浇鑽枫€?
     */
    @Value
    @Builder
    public static class JwtUser {
        Long userId;
        String username;
        String role;
    }
}

