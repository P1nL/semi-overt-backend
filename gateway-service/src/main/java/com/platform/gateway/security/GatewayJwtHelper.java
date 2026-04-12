package com.platform.gateway.security;

import com.platform.web.support.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * 网关侧 JWT 辅助类。
 * 负责解析 token、判断剩余有效期以及在需要时重新签发刷新 token。
 */
@Slf4j
@Component
public class GatewayJwtHelper {

    /** JWT 签名密钥最小字节长度（256 位） */
    private static final int MIN_KEY_BYTES = 32;

    private final JwtProperties jwtProperties;

    /** 缓存的签名密钥，启动时初始化一次 */
    private SecretKey signingKey;

    public GatewayJwtHelper(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 启动时校验并缓存签名密钥。
     */
    @PostConstruct
    void initSigningKey() {
        String key = jwtProperties.getSignKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("[安全] jwt.token.sign-key 未配置，请通过环境变量 JWT_SIGN_KEY 设置");
        }
        byte[] keyBytes = Decoders.BASE64.decode(key);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "[安全] jwt.token.sign-key 长度不足：需要至少 " + MIN_KEY_BYTES + " 字节（256 位），当前 " + keyBytes.length + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("Gateway JWT 签名密钥初始化成功，密钥长度 {} 字节", keyBytes.length);
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
     * 获取缓存的签名密钥。
     */
    private SecretKey getSigningKey() {
        return this.signingKey;
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

