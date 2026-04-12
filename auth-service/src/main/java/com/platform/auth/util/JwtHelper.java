package com.platform.auth.util;

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

import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 工具类，负责 token 的生成、解析和刷新判断。
 */
@Slf4j
@Component
public class JwtHelper {

    /** JWT 签名密钥最小字节长度（256 位） */
    private static final int MIN_KEY_BYTES = 32;

    @Value("${jwt.token.expiration}")
    private long expiration;

    @Value("${jwt.token.remember-me-expiration}")
    private long rememberMeExpiration;

    @Value("${jwt.token.sign-key}")
    private String signKey;

    @Value("${jwt.token.refresh-threshold}")
    private long refreshThreshold;

    /** 缓存的签名密钥，启动时初始化一次 */
    private SecretKey signingKey;

    /**
     * 启动时校验并缓存签名密钥。
     * 密钥 Base64 解码后长度必须 >= 32 字节（256 位），否则拒绝启动。
     */
    @PostConstruct
    void initSigningKey() {
        if (signKey == null || signKey.isBlank()) {
            throw new IllegalStateException("[安全] jwt.token.sign-key 未配置，请通过环境变量 JWT_SIGN_KEY 设置");
        }
        byte[] keyBytes = Decoders.BASE64.decode(signKey);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "[安全] jwt.token.sign-key 长度不足：需要至少 " + MIN_KEY_BYTES + " 字节（256 位），当前 " + keyBytes.length + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT 签名密钥初始化成功，密钥长度 {} 字节", keyBytes.length);
    }

    /**
     * 获取缓存的签名密钥。
     */
    private SecretKey getSigningKey() {
        return this.signingKey;
    }

    /**
     * 为指定用户生成访问 token。
     */
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

    /**
     * 解析 token，并恢复 Spring Security 需要的认证对象。
     * 解析失败或 token 过期时返回 {@code null}。
     */
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
            log.debug("Token expired");
            return null;
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 token 中提取用户 ID；提取失败时返回 {@code null}。
     */
    public Long getUserId(String token) {
        try {
            return Long.valueOf(parseClaims(token).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 token 中提取用户名；提取失败时返回 {@code null}。
     */
    public String getUsername(String token) {
        try {
            return parseClaims(token).get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断 token 是否已经过期；解析失败时也视为已过期。
     */
    public boolean isExpiration(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 计算 token 距离过期还剩多少毫秒；解析失败时返回 0。
     */
    public long getRemainingMillis(String token) {
        try {
            Date expireAt = parseClaims(token).getExpiration();
            long remaining = expireAt.getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 判断 token 是否进入了刷新阈值窗口。
     */
    public boolean shouldRefresh(String token) {
        long remainingMinutes = getRemainingMillis(token) / 60 / 1000;
        return remainingMinutes > 0 && remainingMinutes < refreshThreshold;
    }

    /**
     * 解析 token 的 claims 负载。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 中提取角色字段；提取失败时返回 {@code null}。
     */
    public String getRole(String token) {
        try {
            return parseClaims(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
