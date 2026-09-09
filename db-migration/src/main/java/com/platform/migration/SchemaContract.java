package com.platform.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Source signatures are versioned resources, never inferred from user row contents. */
final class SchemaContract {
    record Column(String type, boolean nullable) {}
    record Table(Map<String, Column> columns, Set<List<String>> uniqueKeys, Map<String,List<String>> indexes) {}
    static String resource(String path) {
        try (var in = SchemaContract.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing schema reference: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException("Cannot read schema reference", e); }
    }
    static Map<String, Table> parse(String sql) {
        Map<String, Table> tables = new LinkedHashMap<>();
        var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\((.*?)\\);|CREATE TABLE IF NOT EXISTS (\\w+) \\((.*?)\\) ENGINE[^;]*;", Pattern.DOTALL).matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String body = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            Map<String, Column> columns = new LinkedHashMap<>();
            Set<List<String>> unique = new HashSet<>();
            Map<String,List<String>> indexes = new HashMap<>();
            for (String line : body.split("\\R")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("PRIMARY KEY") || line.startsWith("UNIQUE KEY")) {
                    List<String> key = keyColumns(line);
                    unique.add(key);
                    if (line.startsWith("PRIMARY")) indexes.put("PRIMARY", key);
                    else indexes.put(line.split("\\s+")[2], key);
                    continue;
                }
                if (line.startsWith("KEY ")) { indexes.put(line.split("\\s+")[1], keyColumns(line)); continue; }
                var col = Pattern.compile("^(\\w+)\\s+([A-Za-z]+(?:\\([^)]*\\))?)(.*)$").matcher(line);
                if (!col.matches()) throw new IllegalStateException("Unsupported reference column in " + name);
                String tail = col.group(3).toUpperCase(Locale.ROOT);
                columns.put(col.group(1), new Column(normalizeType(col.group(2)), !tail.contains("NOT NULL") && !tail.contains("PRIMARY KEY")));
                if (tail.contains("PRIMARY KEY")) { unique.add(List.of(col.group(1))); indexes.put("PRIMARY",List.of(col.group(1))); }
                else if (tail.contains("UNIQUE")) unique.add(List.of(col.group(1)));
            }
            tables.put(name, new Table(columns, unique, indexes));
        }
        if (tables.isEmpty()) throw new IllegalStateException("Empty schema reference");
        return tables;
    }
    private static List<String> keyColumns(String line) {
        return Arrays.stream(line.substring(line.indexOf('(')+1, line.indexOf(')')).split(",")).map(String::trim).toList();
    }
    static String normalizeType(String type) {
        return type.toLowerCase(Locale.ROOT).replace(" ", "").replace("boolean", "tinyint(1)")
                .replaceAll("\\b(bigint|int)\\(\\d+\\)", "$1");
    }
    static Map<String, Table> snapshot(Connection c) throws SQLException {
        Map<String, Map<String,Column>> columns = new LinkedHashMap<>();
        try (var s = c.prepareStatement("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION"); var r=s.executeQuery()) {
            while(r.next()) columns.computeIfAbsent(r.getString(1), k->new LinkedHashMap<>()).put(r.getString(2),new Column(normalizeType(r.getString(3)), "YES".equals(r.getString(4))));
        }
        Map<String,Map<String,List<String>>> indexes = new HashMap<>();
        Map<String,Set<String>> unique = new HashMap<>();
        try (var s=c.prepareStatement("SELECT TABLE_NAME,INDEX_NAME,COLUMN_NAME,NON_UNIQUE,SUB_PART FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX"); var r=s.executeQuery()) {
            while(r.next()) {
                String table=r.getString(1), name=r.getString(2);
                String column=r.getString(3);
                if(r.getObject(5)!=null) column=column+"(prefix)";
                indexes.computeIfAbsent(table,k->new HashMap<>()).computeIfAbsent(name,k->new ArrayList<>()).add(column);
                if(r.getInt(4)==0) unique.computeIfAbsent(table,k->new HashSet<>()).add(name);
            }
        }
        Map<String,Table> result=new LinkedHashMap<>();
        for(var entry:columns.entrySet()) {
            var keys=indexes.getOrDefault(entry.getKey(),Map.of());
            Set<List<String>> uniqueColumns=new HashSet<>();
            for(String key:unique.getOrDefault(entry.getKey(),Set.of())) uniqueColumns.add(keys.get(key));
            result.put(entry.getKey(),new Table(entry.getValue(),uniqueColumns,keys));
        }
        return result;
    }
    static void requireSource(Connection c, boolean monolith, boolean v2) throws SQLException {
        zero(c,"SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()", "unexpected triggers");
        zero(c,"SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()", "unexpected foreign keys; review before DDL");
        zero(c,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE<>'BASE TABLE'", "unexpected views");
        zero(c,"SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND GENERATION_EXPRESSION<>''", "unexpected generated columns");
        var expected=parse(resource(monolith?"/db/reference/monolith-40edf51.sql":"/db/migration/V1__baseline_schema.sql"));
        if(v2) {
            var article=expected.get("articles");
            article.columns().put("last_featured_at",new Column("datetime",true));
            article.indexes().put("idx_articles_featured_status",List.of("status","last_featured_at"));
        }
        zero(c,"SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND ENGINE<>'InnoDB'", "non-InnoDB source table");
        var actual=snapshot(c); actual.remove("flyway_schema_history");
        if(!actual.keySet().equals(expected.keySet())) throw new IllegalStateException("Schema table set differs from approved source");
        for(var entry:expected.entrySet()) {
            String name=entry.getKey(); var want=entry.getValue(); var have=actual.get(name);
            if(!have.columns().equals(want.columns())) throw new IllegalStateException("Source column type/width/nullability mismatch: " + name);
            if(!have.uniqueKeys().containsAll(want.uniqueKeys())) throw new IllegalStateException("Missing source primary/unique constraint: " + name);
            for(var index:want.indexes().entrySet()) if(!index.getValue().equals(have.indexes().get(index.getKey()))) throw new IllegalStateException("Missing/mismatched source index: " + name + "." + index.getKey());
        }
        requireRows(c, monolith);
    }
    static void zero(Connection c, String sql, String reason) throws SQLException {
        try(var s=c.prepareStatement(sql);var r=s.executeQuery()) {
            if(!r.next() || r.getLong(1)!=0) throw new IllegalStateException("Data preflight refused: " + reason);
        }
    }
    static void requireRows(Connection c, boolean monolith) throws SQLException {
        zero(c,"SELECT COUNT(*) FROM users WHERE role NOT IN ('USER','ADMIN')", "unknown role");
        zero(c,"SELECT COUNT(*) FROM articles WHERE status NOT IN ('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') OR duration_category NOT IN ('QUICK','SHORT','DEEP')", "unknown article enum");
        zero(c,"SELECT COUNT(*) FROM articles a LEFT JOIN users u ON u.id=a.author_id WHERE u.id IS NULL", "orphan article author");
        zero(c,"SELECT COUNT(*) FROM review_tasks t LEFT JOIN articles a ON a.id=t.article_id WHERE a.id IS NULL OR a.deleted<>0 OR a.status<>'PENDING' OR t.status<>'PENDING' OR t.author_id<>a.author_id", "review task inconsistent with article");
        zero(c,"SELECT COUNT(*) FROM articles a LEFT JOIN review_tasks t ON t.article_id=a.id WHERE a.status='PENDING' AND a.deleted=0 AND t.article_id IS NULL", "pending article missing review task");
        zero(c,"SELECT COUNT(*) FROM review_logs l LEFT JOIN articles a ON a.id=l.article_id LEFT JOIN users u ON u.id=l.operator_id WHERE a.id IS NULL OR u.id IS NULL OR l.action NOT IN ('APPROVE','REJECT','RETURN','CANCEL')", "invalid review history");
        zero(c,"SELECT COUNT(*) FROM notifications n LEFT JOIN users u ON u.id=n.user_id WHERE u.id IS NULL", "orphan notification");
        zero(c,"SELECT COUNT(*) FROM articles WHERE word_count<0 OR read_minutes<0 OR submit_count<0", "negative article counters");
        if(monolith) {
            zero(c,"SELECT COUNT(*) FROM articles WHERE draft_visible<>0 OR version<0", "public draft or invalid version");
            zero(c,"SELECT COUNT(*) FROM users WHERE session_version<0", "invalid session version");
            zero(c,"SELECT COUNT(*) FROM review_tasks t LEFT JOIN users u ON u.id=t.assigned_admin_id WHERE u.id IS NULL OR u.role<>'ADMIN' OR t.author_id=t.assigned_admin_id", "invalid reviewer assignment");
            zero(c,"SELECT COUNT(*) FROM auth_device_sessions s LEFT JOIN users u ON u.id=s.user_id WHERE u.id IS NULL", "orphan device session");
            zero(c,"SELECT COUNT(*) FROM home_article_exposures e LEFT JOIN users u ON u.id=e.user_id LEFT JOIN articles a ON a.id=e.article_id WHERE u.id IS NULL OR a.id IS NULL OR e.exposure_count<0", "orphan exposure");
            zero(c,"SELECT COUNT(*) FROM password_reset_tokens t LEFT JOIN users u ON u.id=t.user_id WHERE u.id IS NULL", "orphan reset token");
            zero(c,"SELECT COUNT(*) FROM auth_refresh_tokens t LEFT JOIN auth_device_sessions s ON s.session_id=t.session_id WHERE s.session_id IS NULL OR t.family_id<>s.family_id", "orphan refresh token");
        }
    }
}
