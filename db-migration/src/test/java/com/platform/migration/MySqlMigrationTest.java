package com.platform.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="S1_MYSQL_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/")
class MySqlMigrationTest {
    private DataSource fresh() throws Exception {
        String base=System.getenv("S1_MYSQL_URL"), password=System.getenv("S1_MYSQL_PASSWORD");
        String db="s1_"+UUID.randomUUID().toString().replace("-", "");
        try(var c=DriverManager.getConnection(base,"root",password);var s=c.createStatement()) {
            s.execute("CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        return DbMigrationApplication.createConfiguration(base+db+"?serverTimezone=UTC", "root",password).getDataSource();
    }
    private void sql(DataSource ds,String sql) throws Exception {
        try(var c=ds.getConnection();var s=c.createStatement()) { for(String statement:sql.split(";")) if(!statement.isBlank()) s.execute(statement); }
    }
    private long count(DataSource ds,String sql) throws Exception {
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)) { assertTrue(r.next());return r.getLong(1); }
    }
    @Test void emptyAndRerun() throws Exception {
        var ds=fresh(); MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO);
        assertEquals(0,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
        assertEquals(4,count(ds,"SELECT COUNT(*) FROM flyway_schema_history"));
    }
    @Test void v3UpgradeBackfillsSubmissionAndAssignmentWithoutChangingArticleVersion() throws Exception {
        var ds=fresh();
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("3").load().migrate();
        sql(ds,"INSERT INTO users(id,username,email,password,role) VALUES(1,'author','author@example.invalid','hash','ADMIN'),(2,'reviewer','reviewer@example.invalid','hash','ADMIN');"
                +"INSERT INTO articles(id,author_id,title,status,version,submit_count) VALUES(11,1,'pending','PENDING',8,2);"
                +"INSERT INTO review_tasks(article_id,author_id,title,status) VALUES(11,1,'pending','PENDING')");
        assertEquals(1,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM articles WHERE id=11 AND version=8 AND submission_id='legacy-11-2'"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM review_tasks WHERE article_id=11 AND assigned_admin_id=2 AND last_applied_version=8 AND submission_id='legacy-11-2'"));
        // A committed cancellation can temporarily precede its review projection.
        sql(ds,"UPDATE articles SET status='DRAFT',version=9 WHERE id=11");
        assertEquals(0,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
        sql(ds,"UPDATE review_tasks SET status='DRAFT',last_applied_version=9,command_state='CLOSED' WHERE article_id=11");
        assertEquals(0,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
    }
    @Test void partialS3DdlAndMissingDecisionUniquenessFailClosed() throws Exception {
        var partial=fresh();
        org.flywaydb.core.Flyway.configure().dataSource(partial).locations("classpath:db/migration").target("3").load().migrate();
        sql(partial,"ALTER TABLE event_outbox ADD COLUMN lease_owner VARCHAR(128) NULL");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(partial,MigrationRunner.Mode.AUTO));
        assertEquals(0,count(partial,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='4'"));
        var drift=fresh();MigrationRunner.migrate(drift,MigrationRunner.Mode.AUTO);
        sql(drift,"ALTER TABLE review_logs DROP INDEX uk_review_log_decision");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(drift,MigrationRunner.Mode.AUTO));
    }
    @Test void referenceParserDoesNotSwallowInterveningAlterStatements() {
        var parsed=SchemaContract.parse(SchemaContract.resource("/db/migration/V4__s3_state_consistency.sql"));
        assertEquals(java.util.Set.of("content_author_locks","content_review_decisions","review_commands"),parsed.keySet());
        assertEquals(1,parsed.get("content_author_locks").columns().size());
    }
    @Test void legacyInFlightProtocolRefusesBeforeS3Ddl() throws Exception {
        var ds=fresh();
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("3").load().migrate();
        sql(ds,"INSERT INTO event_outbox(event_id,aggregate_type,aggregate_id,event_type,payload,status) VALUES('old-review','review','1','ReviewDecidedEvent','{}','PENDING')");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO));
        assertEquals(0,count(ds,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='articles' AND column_name='submission_id'"));
        assertEquals(0,count(ds,"SELECT COUNT(*) FROM flyway_schema_history WHERE version='4'"));
    }
    @Test void legacyV1AndV2OnboardingPreserveData() throws Exception {
        for(var mode:new MigrationRunner.Mode[]{MigrationRunner.Mode.LEGACY_V1,MigrationRunner.Mode.LEGACY_V2}) {
            var ds=fresh();sql(ds,SchemaContract.resource("/db/migration/V1__baseline_schema.sql"));
            if(mode==MigrationRunner.Mode.LEGACY_V2) sql(ds,SchemaContract.resource("/db/migration/V2__add_last_featured_at_to_articles.sql"));
            sql(ds,"INSERT INTO users(id,username,nickname,email,password) VALUES(41,'writer','Writer','writer@example.invalid','preserved-hash')");
            assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO));
            MigrationRunner.migrate(ds,mode);
            assertEquals(1,count(ds,"SELECT COUNT(*) FROM users WHERE id=41 AND password='preserved-hash'"));
            assertEquals(0,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
        }
    }
    @Test void monolithPreservesHistorySessionsAssignmentAndVersions() throws Exception {
        var ds=fresh();sql(ds,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        sql(ds,"INSERT INTO users(id,username,email,password_hash,session_version,role) VALUES(41,'writer','w@example.invalid','kept-hash',7,'USER'),(42,'admin','a@example.invalid','admin-hash',2,'ADMIN');"
            +"INSERT INTO articles(id,author_id,title,content,status,version) VALUES(51,41,'kept title','kept body','PENDING',12);"
            +"INSERT INTO review_tasks(article_id,author_id,assigned_admin_id,status,submitted_at) VALUES(51,41,42,'PENDING',CURRENT_TIMESTAMP);"
            +"INSERT INTO review_logs(id,article_id,operator_id,action,reason) VALUES(61,51,42,'RETURN','historical reason');"
            +"INSERT INTO notifications(id,user_id,type,title,content) VALUES(71,41,'REVIEW','History','keep notification');"
            +"INSERT INTO auth_device_sessions(session_id,user_id,family_id,status,created_at,last_used_at,idle_expires_at,absolute_expires_at,persistent) VALUES('device',41,'family','ACTIVE',NOW(),NOW(),NOW(),NOW(),false);"
            +"INSERT INTO auth_refresh_tokens(token_hash,session_id,family_id,created_at) VALUES('hash','device','family',NOW())");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO));
        MigrationRunner.migrate(ds,MigrationRunner.Mode.MONOLITH);
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM users WHERE id=41 AND password='kept-hash' AND session_version=7"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM articles WHERE id=51 AND version=12 AND content='kept body'"));
        assertEquals(42,count(ds,"SELECT assigned_admin_id FROM review_tasks WHERE article_id=51"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM review_logs WHERE id=61 AND from_status IS NULL AND to_status IS NULL AND reason='historical reason'"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM notifications WHERE id=71 AND biz_id IS NULL"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM auth_device_sessions WHERE session_id='device' AND persistent=false"));
        assertEquals(1,count(ds,"SELECT COUNT(*) FROM auth_refresh_tokens WHERE token_hash='hash'"));
        assertEquals(0,MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO).migrationsExecuted);
    }
    @Test void rejectsDriftAndOrphansBeforeHistoryCreation() throws Exception {
        var ds=fresh();sql(ds,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        sql(ds,"ALTER TABLE users MODIFY email VARCHAR(90) NOT NULL");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.MONOLITH));
        assertEquals(0,count(ds,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'"));
        var orphan=fresh();sql(orphan,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        sql(orphan,"INSERT INTO articles(author_id,title) VALUES(999,'orphan')");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(orphan,MigrationRunner.Mode.MONOLITH));
    }
    @Test void versionedLegacyUpgradesAndChecksumTamperingIsRejected() throws Exception {
        var ds=fresh();
        org.flywaydb.core.Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("2").load().migrate();
        MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO);
        sql(ds,"UPDATE flyway_schema_history SET checksum=1 WHERE version='3'");
        assertThrows(org.flywaydb.core.api.FlywayException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO));
    }
    @Test void wrongModeAndMissingUniqueConstraintDoNotCreateHistory() throws Exception {
        var ds=fresh(); sql(ds,SchemaContract.resource("/db/migration/V1__baseline_schema.sql"));
        sql(ds,SchemaContract.resource("/db/migration/V2__add_last_featured_at_to_articles.sql"));
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.LEGACY_V1));
        sql(ds,"ALTER TABLE users DROP INDEX uk_email");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.LEGACY_V2));
        assertEquals(0,count(ds,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'"));
    }
    @Test void ddlLockContentionRefusesWithoutChanges() throws Exception {
        var ds=fresh();
        try(var connection=ds.getConnection();var statement=connection.createStatement()) {
            statement.execute("SELECT GET_LOCK(CONCAT('semi-sync:',DATABASE()),0)");
            assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(ds,MigrationRunner.Mode.AUTO));
            assertEquals(0,count(ds,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
        }
    }    @Test void fulltextIndexSurvivesMonolithConversion() throws Exception {
        var ds=fresh();sql(ds,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        sql(ds,"CREATE FULLTEXT INDEX ft_articles_search ON articles(title,summary,content) WITH PARSER ngram");
        MigrationRunner.migrate(ds,MigrationRunner.Mode.MONOLITH);
        assertEquals(3,count(ds,"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name='ft_articles_search' AND index_type='FULLTEXT'"));
    }
    @Test void partialDdlRefusesAutomaticRetryAndRestoredSnapshotWorks() throws Exception {
        var partial=fresh();sql(partial,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        sql(partial,"ALTER TABLE users RENAME COLUMN password_hash TO password");
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(partial,MigrationRunner.Mode.MONOLITH));
        assertThrows(IllegalStateException.class,()->MigrationRunner.migrate(partial,MigrationRunner.Mode.AUTO));
        // Restore the frozen source snapshot into a fresh isolated schema, never repair partial DDL blindly.
        var restored=fresh();sql(restored,SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        MigrationRunner.migrate(restored,MigrationRunner.Mode.MONOLITH);
        assertEquals(0,MigrationRunner.migrate(restored,MigrationRunner.Mode.AUTO).migrationsExecuted);
    }    @Test void fullMonolithResourceRemainsFrozen() {
        var source=SchemaContract.parse(SchemaContract.resource("/db/reference/monolith-40edf51.sql"));
        assertEquals(11,source.size());
        assertEquals(new SchemaContract.Column("varchar(120)",false),source.get("users").columns().get("password_hash"));
    }}
