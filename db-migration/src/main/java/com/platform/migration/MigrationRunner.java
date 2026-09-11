package com.platform.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Executes only approved source onboarding routes, serialized by a database advisory lock. */
final class MigrationRunner {
    enum Mode { AUTO, LEGACY_V1, LEGACY_V2, MONOLITH }
    static MigrateResult migrate(DataSource source, Mode mode) throws SQLException {
        try(Connection guard=source.getConnection()) {
            try(var s=guard.prepareStatement("SELECT GET_LOCK(CONCAT('semi-sync:', DATABASE()), 0)");var r=s.executeQuery()) {
                if(!r.next() || r.getInt(1)!=1) throw new IllegalStateException("Migration lock unavailable");
            }
            try { return locked(source, guard, mode); }
            finally {
                try(var s=guard.prepareStatement("SELECT RELEASE_LOCK(CONCAT('semi-sync:', DATABASE()))")) { s.execute(); }
            }
        }
    }
    private static MigrateResult locked(DataSource source, Connection c, Mode mode) throws SQLException {
        SchemaPreflight.Report report=SchemaPreflight.inspect(c);
        boolean monolith=mode==Mode.MONOLITH;
        boolean v3=false;
        boolean v4=false;
        if(report.historyPresent()) {
            if(mode!=Mode.AUTO) throw new IllegalStateException("Existing history requires AUTO, not onboarding");
            try(var s=c.prepareStatement("SELECT version, description, script, success FROM flyway_schema_history ORDER BY installed_rank");var r=s.executeQuery()) {
                while(r.next()) {
                    if(!r.getBoolean(4)) throw new IllegalStateException("Failed migration history; restore/review before retry");
                    String version=r.getString(1);
                    if(version==null || !Set.of("1","2","3","4").contains(version)) throw new IllegalStateException("Unexpected migration history version");
                    if("S1_MONOLITH".equals(r.getString(2)) || "V3__sync_monolith_foundation.sql".equals(r.getString(3))) monolith=true;
                    if("3".equals(version)) v3=true;
                    if("4".equals(version)) v4=true;
                }
            }
        }
        String location=monolith?"classpath:db/monolith":"classpath:db/migration";
        if(!v4) requireS3ProtocolCutover(c);
        Flyway flyway=Flyway.configure().dataSource(source).baselineOnMigrate(false).ignoreMigrationPatterns("*:pending").locations(location).load();
        if(report.historyPresent()) {
            flyway.validate();
            if(v3) {
                requireTarget(c,v4);
                MigrateResult result=flyway.migrate();
                requireTarget(c,true);
                return result;
            }
            // Interrupted MySQL DDL must never be silently baselined/repaired.
            boolean appliedV2=Arrays.stream(flyway.info().applied()).anyMatch(i->i.getVersion()!=null && "2".equals(i.getVersion().toString()));
            if(monolith) SchemaContract.requireSource(c,true,false);
            else {
                if(appliedV2!=report.featuredColumnPresent()) throw new IllegalStateException("V2 history and column disagree");
                SchemaContract.requireSource(c,false,appliedV2);
            }
        } else if(report.kind()==SchemaPreflight.Kind.EMPTY) {
            if(mode!=Mode.AUTO) throw new IllegalStateException("Empty database requires AUTO");
        } else {
            if(mode==Mode.AUTO) throw new IllegalStateException("Nonempty database requires explicit approved onboarding mode");
            if(monolith && report.kind()!=SchemaPreflight.Kind.MONOLITH) throw new IllegalStateException("MONOLITH mode requires monolith source");
            if(!monolith && report.kind()!=SchemaPreflight.Kind.LEGACY_MICROSERVICE) throw new IllegalStateException("Legacy mode requires legacy source");
            boolean v2=mode==Mode.LEGACY_V2;
            if(!monolith && report.featuredColumnPresent()!=v2) throw new IllegalStateException("Choose matching explicit V1/V2 source mode");
            SchemaContract.requireSource(c,monolith,v2);
            flyway=Flyway.configure().dataSource(source).baselineOnMigrate(false).ignoreMigrationPatterns("*:pending").locations(location)
                    .baselineVersion(monolith||v2?"2":"1")
                    .baselineDescription(monolith?"S1_MONOLITH":"S1_LEGACY").load();
            flyway.baseline();
        }
        MigrateResult result=flyway.migrate();
        requireTarget(c,true);
        return result;
    }
    static void requireTarget(Connection c) throws SQLException {
        requireTarget(c,true);
    }
    private static void requireS3ProtocolCutover(Connection c) throws SQLException {
        var tables=SchemaContract.snapshot(c);
        if(tables.containsKey("event_outbox")) {
            // A v1 review event meant "already logged", while S3 treats it as a versioned
            // command. Never guess a generation or silently replay this protocol boundary.
            SchemaContract.zero(c,"SELECT COUNT(*) FROM event_outbox WHERE status IN ('PENDING','DEAD')",
                    "S3 requires old outbox drained or explicitly reconciled before protocol cutover");
        }
        if(tables.containsKey("event_consume_log")) {
            SchemaContract.zero(c,"SELECT COUNT(*) FROM event_consume_log WHERE status<>'SUCCESS'",
                    "S3 requires unresolved legacy inbox attempts reconciled before cutover");
        }
    }
    static void requireTarget(Connection c, boolean s3) throws SQLException {
        SchemaContract.zero(c,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE<>'BASE TABLE'", "unexpected target views");
        var tables=SchemaContract.snapshot(c);
        Set<String> expected=new HashSet<>(SchemaContract.parse(SchemaContract.resource("/db/reference/monolith-40edf51.sql")).keySet());
        expected.addAll(List.of("event_outbox","event_consume_log","notification_deliveries","flyway_schema_history"));
        if(s3) expected.addAll(List.of("content_author_locks","content_review_decisions","review_commands"));
        if(!tables.keySet().equals(expected)) throw new IllegalStateException("Unexpected target tables");
        if(!s3) {
            if(tables.get("articles").columns().containsKey("submission_id")
                    || tables.get("review_tasks").columns().containsKey("last_applied_version")
                    || tables.get("review_logs").columns().containsKey("decision_id")
                    || tables.get("notifications").columns().containsKey("decision_id")) {
                throw new IllegalStateException("Partial S3 schema without V4 history; restore/review before retry");
            }
        }
        var users=tables.get("users").columns();
        if(users.containsKey("password_hash") || !new SchemaContract.Column("varchar(120)",false).equals(users.get("password"))) throw new IllegalStateException("Target password mapping invalid");
        for(String column:List.of("version","draft_visible","last_featured_at")) if(!tables.get("articles").columns().containsKey(column)) throw new IllegalStateException("Target article column missing: "+column);
        if(!tables.get("review_tasks").columns().containsKey("assigned_admin_id")) throw new IllegalStateException("Target assignment missing");
        Map<String,Integer> widths=Map.of("username",32,"nickname",60,"email",120,"cover_url",512,"signature",100);
        for(var width:widths.entrySet()) {
            var column=users.get(width.getKey());
            if(column==null || !column.type().equals("varchar("+width.getValue()+")")) throw new IllegalStateException("Target user width mismatch: "+width.getKey());
        }
        if(!new SchemaContract.Column("bigint",false).equals(users.get("session_version"))) throw new IllegalStateException("Target session_version missing");
        var article=tables.get("articles");
        if(!new SchemaContract.Column("longtext",true).equals(article.columns().get("content"))
                || !new SchemaContract.Column("varchar(512)",true).equals(article.columns().get("cover_url"))
                || !new SchemaContract.Column("varchar(32)",true).equals(article.columns().get("cover_color"))
                || !new SchemaContract.Column("bigint",false).equals(article.columns().get("version"))) throw new IllegalStateException("Target article column contract mismatch");
        if(!List.of("status","last_featured_at").equals(article.indexes().get("idx_articles_featured_status"))) throw new IllegalStateException("Target featured index mismatch");
        for(String name:List.of("rate_limit_buckets","auth_device_sessions","auth_refresh_tokens","password_reset_tokens","email_verification_codes","home_article_exposures")) {
            var reference=SchemaContract.parse(SchemaContract.resource("/db/reference/monolith-40edf51.sql")).get(name);
            if(!tables.get(name).columns().equals(reference.columns()) || !tables.get(name).uniqueKeys().containsAll(reference.uniqueKeys())) throw new IllegalStateException("Target foundation table mismatch: "+name);
        }
        for(String name:List.of("event_outbox","event_consume_log","notification_deliveries")) {
            var reference=SchemaContract.parse(SchemaContract.resource("/db/migration/V1__baseline_schema.sql")).get(name);
            if(s3 && name.equals("event_outbox")) {
                reference.columns().put("lease_owner",new SchemaContract.Column("varchar(128)",true));
                reference.columns().put("lease_token",new SchemaContract.Column("varchar(64)",true));
                reference.columns().put("lease_until",new SchemaContract.Column("datetime(6)",true));
            }
            if(!tables.get(name).columns().equals(reference.columns()) || !tables.get(name).uniqueKeys().containsAll(reference.uniqueKeys())) throw new IllegalStateException("Target event table mismatch: "+name);
        }
        if(!tables.get("review_logs").columns().get("from_status").nullable()
                || !tables.get("review_logs").columns().get("to_status").nullable()
                || !tables.get("notifications").columns().get("biz_id").nullable()) throw new IllegalStateException("Legacy unknown fields must allow NULL");
        if(s3) S3SchemaContract.require(c,tables);
        SchemaContract.requireRows(c,false,s3);
    }
}
