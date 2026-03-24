package com.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 工具类
 * Token 结构：subject=userId, claims: username + role
 * 认证方式：Authorization: Bearer <token>
 */
@Slf4j
@Component
public class JwtHelper {

    @Value("${jwt.token.expiration}")
    private long expiration;

    @Value("${jwt.token.remember-me-expiration}")
    private long rememberMeExpiration;

    /**
     * 建议在配置文件中放 Base64 编码后的 64 字节密钥
     */
    @Value("${jwt.token.sign-key}")
    private String signKey;

    @Value("${jwt.token.refresh-threshold}")
    private long refreshThreshold;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(signKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(Long userId, String username, String role, boolean rememberMe) {
        long ttlMinutes = rememberMe ? rememberMeExpiration : expiration;
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

    public UsernamePasswordAuthenticationToken resolveJwt(String token) {
        try {
            Claims claims = parseClaims(token);

            String role = claims.get("role", String.class);
            Long userId = Long.valueOf(claims.getSubject());

            List<SimpleGrantedAuthority> authorities = Arrays.stream(role.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            return new UsernamePasswordAuthenticationToken(userId, null, authorities);
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            return null;
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public Long getUserId(String token) {
        try {
            return Long.valueOf(parseClaims(token).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public String getUsername(String token) {
        try {
            return parseClaims(token).get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isExpiration(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public long getRemainingMillis(String token) {
        try {
            Date expireAt = parseClaims(token).getExpiration();
            long remaining = expireAt.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean shouldRefresh(String token) {
        long remainingMinutes = getRemainingMillis(token) / 60 / 1000;
        return remainingMinutes > 0 && remainingMinutes < refreshThreshold;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getRole(String token) {
        try {
            return parseClaims(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}