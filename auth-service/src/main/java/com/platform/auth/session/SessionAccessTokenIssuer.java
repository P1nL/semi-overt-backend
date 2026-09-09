package com.platform.auth.session;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/** Short access token issuer and verifier for durable device sessions. */
@Component
public final class SessionAccessTokenIssuer {
    private final SecretKey key;
    private final long ttlSeconds;
    public SessionAccessTokenIssuer(@Value("${jwt.token.sign-key}") String base64Key,
                                    @Value("${platform.auth.access-token-seconds:900}") long ttlSeconds) {
        if(ttlSeconds<1 || ttlSeconds>900) throw new IllegalArgumentException("Access TTL must be between 1 and 900 seconds");
        this.key=Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Key));
        this.ttlSeconds=ttlSeconds;
    }
    public String issue(long userId,String username,String role,String sessionId,long sessionVersion) {
        Instant now=Instant.now();
        return Jwts.builder().subject(Long.toString(userId)).claim("username",username).claim("role",role)
                .claim("sid",sessionId).claim("sessionVersion",sessionVersion)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds))).signWith(key).compact();
    }
    public io.jsonwebtoken.Claims validate(String token) {
        var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if(claims.getExpiration()==null || claims.getIssuedAt()==null
                || claims.getExpiration().getTime()-claims.getIssuedAt().getTime()>900000
                || claims.getIssuedAt().after(new Date(System.currentTimeMillis()+30000))
                || claims.getExpiration().before(claims.getIssuedAt())
                || claims.get("sid",String.class)==null || claims.get("sid",String.class).isBlank()
                || claims.get("sessionVersion",Number.class)==null)
            throw new IllegalArgumentException("Not a device access token");
        return claims;
    }
}
