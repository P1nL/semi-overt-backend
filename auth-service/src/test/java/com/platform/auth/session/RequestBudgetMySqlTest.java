package com.platform.auth.session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="S2_MYSQL_URL",matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
class RequestBudgetMySqlTest {
    @Test void atomicMultiDimensionPersistsAcrossInstancesAndRejectsWithoutPartialConsumption() throws Exception {
        String base=System.getenv("S2_MYSQL_URL"),pass=System.getenv("S2_MYSQL_PASSWORD"),db="s2_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(base,"root",pass);var s=c.createStatement()){s.execute("CREATE DATABASE `"+db+"`");}
        var ds=new DriverManagerDataSource(base+db,"root",pass);var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE rate_limit_buckets(bucket_key VARCHAR(160) PRIMARY KEY,window_started_at BIGINT,request_count BIGINT,expires_at BIGINT)");
        var manager=new DataSourceTransactionManager(ds);Clock clock=Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"),ZoneOffset.UTC);
        var one=new JdbcRequestBudget(jdbc,manager,clock);var two=new JdbcRequestBudget(jdbc,manager,clock);
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(8);
        try{
            List<Future<Boolean>> futures=new ArrayList<>();
            for(int i=0;i<20;i++){var store=i%2==0?one:two;futures.add(executor.submit(()->{start.await();return store.tryAcquire(List.of(new JdbcRequestBudget.Limit("shared",5,60))).allowed();}));}
            start.countDown();int accepted=0;for(var f:futures)if(f.get(15,TimeUnit.SECONDS))accepted++;
            assertEquals(5,accepted);assertEquals(5,jdbc.queryForObject("SELECT request_count FROM rate_limit_buckets WHERE bucket_key='shared'",Integer.class));
        } finally {executor.shutdownNow();}
        assertFalse(one.tryAcquire(List.of(new JdbcRequestBudget.Limit("new-key",5,60),new JdbcRequestBudget.Limit("shared",5,60))).allowed());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_buckets WHERE bucket_key='new-key'",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT window_started_at FROM rate_limit_buckets WHERE bucket_key='shared'",Long.class)>1_000_000_000_000L);
        var later=new JdbcRequestBudget(jdbc,manager,Clock.offset(clock,Duration.ofSeconds(61)));
        assertTrue(later.tryAcquire(List.of(new JdbcRequestBudget.Limit("shared",5,60))).allowed());
        var laterStill=new JdbcRequestBudget(jdbc,manager,Clock.offset(clock,Duration.ofSeconds(300)));
        laterStill.cleanupExpired();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_buckets",Integer.class));
    }
}
