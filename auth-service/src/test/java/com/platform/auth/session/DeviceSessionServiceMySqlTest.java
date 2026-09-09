package com.platform.auth.session;

import com.platform.auth.repository.JdbcDeviceSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="S2_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
class DeviceSessionServiceMySqlTest {
    private static final byte[] KEY=new byte[32];
    @Test void rotatesHashedTokenAndCommitsReplayBeforeOuterFailure() throws Exception {
        String base=System.getenv("S2_MYSQL_URL"),pass=System.getenv("S2_MYSQL_PASSWORD");
        String db="s2_"+UUID.randomUUID().toString().replace("-", "");
        try(var c=DriverManager.getConnection(base,"root",pass);var s=c.createStatement()){s.execute("CREATE DATABASE `"+db+"`");}
        var ds=new DriverManagerDataSource(base+db+"?serverTimezone=UTC","root",pass);var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username VARCHAR(32),role VARCHAR(20),session_version BIGINT)");
        jdbc.update("INSERT INTO users VALUES(1,'writer','USER',7)");
        try(var in=getClass().getResourceAsStream("/s2/foundation-reference.sql")) {
            String sql=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var m=java.util.regex.Pattern.compile("CREATE TABLE (auth_device_sessions|auth_refresh_tokens) \\((.*?)\\);",java.util.regex.Pattern.DOTALL).matcher(sql);
            while(m.find())jdbc.execute(m.group());
        }
        var manager=new DataSourceTransactionManager(ds);
        var service=new DeviceSessionService(new JdbcDeviceSessionRepository(jdbc),jdbc,new SessionAccessTokenIssuer(Base64.getEncoder().encodeToString(KEY),900),manager,30,90);
        var first=service.openForAuthenticatedUser(1,false,null);assertEquals(DeviceSessionService.Status.ISSUED,first.status());
        var tokens=first.tokens();assertFalse(tokens.persistent());assertEquals(43,tokens.refreshToken().length());
        String stored=jdbc.queryForObject("SELECT token_hash FROM auth_refresh_tokens",String.class);
        assertEquals(DeviceSessionService.hash(tokens.refreshToken()),stored);assertNotEquals(tokens.refreshToken(),stored);
        var claims=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(KEY)).build().parseSignedClaims(tokens.accessToken()).getPayload();
        assertEquals(tokens.sessionId(),claims.get("sid"));assertEquals(7,((Number)claims.get("sessionVersion")).longValue());
        assertEquals(900000,claims.getExpiration().getTime()-claims.getIssuedAt().getTime());
        var refreshed=service.refresh(tokens.refreshToken());assertEquals(DeviceSessionService.Status.ISSUED,refreshed.status());assertFalse(refreshed.tokens().persistent());
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(s->{
            assertEquals(DeviceSessionService.Status.REPLAYED,service.refresh(tokens.refreshToken()).status());
            throw new IllegalStateException("HTTP failure simulated outside durable transaction");
        }));
        assertEquals("REVOKED",jdbc.queryForObject("SELECT status FROM auth_device_sessions",String.class));
        assertEquals(DeviceSessionService.Status.EXPIRED,service.refresh(refreshed.tokens().refreshToken()).status());
        var again=service.openForAuthenticatedUser(1,true,null);service.logout(again.tokens().refreshToken());service.logout(null);
        assertEquals(DeviceSessionService.Status.EXPIRED,service.refresh(again.tokens().refreshToken()).status());
        assertEquals(DeviceSessionService.Status.UNKNOWN,service.refresh("invalid").status());
        assertEquals(DeviceSessionService.Status.USER_MISSING,service.openForAuthenticatedUser(999,true,null).status());
        assertFalse(tokens.toString().contains(tokens.refreshToken()));        assertNull(service.validateAccess(tokens.accessToken()));
        var valid=service.openForAuthenticatedUser(1,7L,true,null);
        assertNotNull(service.validateAccess(valid.tokens().accessToken()));
        jdbc.update("UPDATE users SET session_version=8 WHERE id=1");
        assertNull(service.validateAccess(valid.tokens().accessToken()));
        assertEquals(DeviceSessionService.Status.VERSION_CHANGED,service.openForAuthenticatedUser(1,7L,true,null).status());
        assertNull(service.validateAccess("not-a-jwt"));        String legacy=Jwts.builder().subject("1").claim("role","ADMIN").issuedAt(new java.util.Date()).expiration(new java.util.Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor(KEY)).compact();
        assertNull(service.validateAccess(legacy));
    }
    @Test void rejectsUnboundedAccessTtl() {
        assertThrows(IllegalArgumentException.class,()->new SessionAccessTokenIssuer(Base64.getEncoder().encodeToString(KEY),901));
    }
}
