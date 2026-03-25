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

@Component
public class GatewayJwtHelper {

    private final JwtProperties jwtProperties;

    public GatewayJwtHelper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

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

    public long getRemainingMillis(String token) {
        try {
            Date expireAt = parseClaims(token).getExpiration();
            return Math.max(expireAt.getTime() - System.currentTimeMillis(), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean shouldRefresh(String token) {
        long remainingMinutes = getRemainingMillis(token) / 1000 / 60;
        return remainingMinutes > 0 && remainingMinutes < jwtProperties.getRefreshThreshold();
    }

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

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSignKey());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Value
    @Builder
    public static class JwtUser {
        Long userId;
        String username;
        String role;
    }
}
