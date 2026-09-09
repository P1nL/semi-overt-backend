package com.platform.auth.repository;

import com.platform.auth.model.RefreshTokenRotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="S2_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
class DeviceSessionMySqlTest {
    record Fixture(JdbcTemplate jdbc, JdbcDeviceSessionRepository repo, TransactionTemplate tx) {}
    Fixture fixture() throws Exception {
        String base=System.getenv("S2_MYSQL_URL"),pass=System.getenv("S2_MYSQL_PASSWORD");
        String db="s2_"+UUID.randomUUID().toString().replace("-", "");
        try(var c=DriverManager.getConnection(base,"root",pass);var s=c.createStatement()){s.execute("CREATE DATABASE `"+db+"`");}
        var ds=new DriverManagerDataSource(base+db+"?serverTimezone=UTC","root",pass);
        var jdbc=new JdbcTemplate(ds);
        try(var in=getClass().getResourceAsStream("/s2/foundation-reference.sql")) {
            String sql=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            // Execute only the two S1-owned session tables, not ALTERs or other domains.
            var matcher=java.util.regex.Pattern.compile("CREATE TABLE (auth_device_sessions|auth_refresh_tokens) \\((.*?)\\);",java.util.regex.Pattern.DOTALL).matcher(sql);
            int tables=0;while(matcher.find()){jdbc.execute(matcher.group());tables++;}assertEquals(2,tables);
        }
        return new Fixture(jdbc,new JdbcDeviceSessionRepository(jdbc),new TransactionTemplate(new DataSourceTransactionManager(ds)));
    }
    void create(Fixture f,String id,long user,boolean persistent,LocalDateTime now) {
        f.tx.executeWithoutResult(s->f.repo.createSession(id,user,"family-"+id,"hash-"+id,now,now.plusDays(1),now.plusDays(7),persistent));
    }
    @Test void rotationPreservesPersistenceAndReplayRevokesOnlyItsDevice() throws Exception {
        var f=fixture();var now=LocalDateTime.now();create(f,"a",1,false,now);create(f,"b",1,true,now);
        var rotated=f.tx.execute(s->f.repo.rotateRefreshToken("hash-a","new-a",now.plusMinutes(1),now.plusDays(2)));
        assertEquals(RefreshTokenRotation.Status.ROTATED,rotated.status());assertFalse(rotated.persistent());
        var replay=f.tx.execute(s->f.repo.rotateRefreshToken("hash-a","again",now.plusMinutes(2),now.plusDays(2)));
        assertEquals(RefreshTokenRotation.Status.REPLAYED,replay.status());
        assertFalse(f.repo.touchForAccess("a",1L,now.plusMinutes(3),now.plusDays(1)));
        assertTrue(f.repo.touchForAccess("b",1L,now.plusMinutes(3),now.plusDays(1)));
    }
    @Test void logoutAndGlobalRevocationAreDurable() throws Exception {
        var f=fixture();var now=LocalDateTime.now();create(f,"a",1,true,now);create(f,"b",1,true,now);create(f,"c",2,true,now);
        f.tx.executeWithoutResult(s->f.repo.revokeByRefreshToken("hash-a",now));
        assertFalse(f.repo.touchForAccess("a",1L,now,now.plusDays(1)));
        f.tx.executeWithoutResult(s->f.repo.revokeAllForUser(1L,now));
        assertFalse(f.repo.touchForAccess("b",1L,now,now.plusDays(1)));
        assertTrue(f.repo.touchForAccess("c",2L,now,now.plusDays(1)));
    }
    @Test void expiredAndUnknownTokensNeverRotate() throws Exception {
        var f=fixture();var now=LocalDateTime.now();create(f,"old",1,true,now.minusDays(8));
        assertEquals(RefreshTokenRotation.Status.EXPIRED,f.tx.execute(s->f.repo.rotateRefreshToken("hash-old","new",now,now.plusDays(1))).status());
        assertEquals(RefreshTokenRotation.Status.UNKNOWN,f.tx.execute(s->f.repo.rotateRefreshToken("missing","new2",now,now.plusDays(1))).status());
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_tokens",Integer.class));
    }
    @Test void concurrentRefreshHasOneWinnerAndReplayRevokesWinner() throws Exception {
        var f=fixture();var now=LocalDateTime.now();create(f,"a",1,true,now);
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            List<Future<RefreshTokenRotation.Status>> futures=new ArrayList<>();
            for(int i=0;i<2;i++){final int n=i;futures.add(executor.submit(()->{start.await();return f.tx.execute(s->f.repo.rotateRefreshToken("hash-a","replacement-"+n,now.plusSeconds(1),now.plusDays(1))).status();}));}
            start.countDown();var states=new HashSet<RefreshTokenRotation.Status>();for(var future:futures)states.add(future.get(15,TimeUnit.SECONDS));
            assertEquals(Set.of(RefreshTokenRotation.Status.ROTATED,RefreshTokenRotation.Status.REPLAYED),states);
            assertEquals("REVOKED",f.jdbc.queryForObject("SELECT status FROM auth_device_sessions WHERE session_id='a'",String.class));
        } finally {executor.shutdownNow();}
    }
    @Test void insertFailureRollsBackConsumption() throws Exception {
        var f=fixture();var now=LocalDateTime.now();create(f,"a",1,true,now);create(f,"b",2,true,now);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->f.tx.execute(s->f.repo.rotateRefreshToken("hash-a","hash-b",now,now.plusDays(1))));
        assertNull(f.jdbc.queryForObject("SELECT consumed_at FROM auth_refresh_tokens WHERE token_hash='hash-a'",Timestamp.class));
        assertEquals(RefreshTokenRotation.Status.ROTATED,f.tx.execute(s->f.repo.rotateRefreshToken("hash-a","valid",now,now.plusDays(1))).status());
    }
}
