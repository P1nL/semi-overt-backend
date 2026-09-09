package com.platform.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Read-only shape guard, not a substitute for schema/data migration validation. */
final class SchemaPreflight {
    enum Kind { EMPTY, LEGACY_MICROSERVICE, MONOLITH, UNKNOWN }
    record Report(Kind kind, boolean historyPresent, boolean featuredColumnPresent) {
        void requireMigrationAllowed() {
            if (kind == Kind.EMPTY && !historyPresent) return;
            if (kind == Kind.LEGACY_MICROSERVICE && historyPresent) return;
            throw new IllegalStateException("Migration refused: schema=" + kind
                    + ", history=" + historyPresent
                    + ". Explicit schema onboarding/conversion is required; do not baseline to bypass this guard.");
        }
    }

    private static final Set<String> LEGACY_TABLES = Set.of("users", "articles", "review_logs",
            "review_tasks", "event_outbox", "event_consume_log", "notifications", "notification_deliveries");
    private static final Set<String> MONOLITH_TABLES = Set.of("users", "articles", "review_logs",
            "review_tasks", "notifications", "rate_limit_buckets", "auth_device_sessions", "auth_refresh_tokens",
            "password_reset_tokens", "email_verification_codes", "home_article_exposures");
    private static final Map<String, Set<String>> LEGACY_KEYS = Map.of(
            "users", Set.of("id", "username", "password", "role"),
            "articles", Set.of("id", "author_id", "content", "status", "deleted", "submit_count"),
            "review_logs", Set.of("id", "article_id", "from_status", "to_status"),
            "review_tasks", Set.of("id", "article_id", "last_event_id"),
            "event_outbox", Set.of("event_id", "aggregate_id", "payload", "status"),
            "event_consume_log", Set.of("event_id", "consumer", "status"),
            "notifications", Set.of("id", "user_id", "biz_id", "read_status"),
            "notification_deliveries", Set.of("id", "notification_id", "channel", "status"));

    // DATABASE() prevents a missing catalog from silently scanning other databases.
    static final String SQL = "SELECT t.TABLE_NAME, t.TABLE_TYPE, c.COLUMN_NAME "
            + "FROM information_schema.TABLES t LEFT JOIN information_schema.COLUMNS c "
            + "ON c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME "
            + "WHERE t.TABLE_SCHEMA=DATABASE()";

    static Report inspect(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()" );
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || rows.getString(1) == null || rows.getString(1).isBlank()) {
                throw new IllegalStateException("Migration refused: no selected MySQL database.");
            }
        }
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        boolean unsupportedObject = false;
        try (PreparedStatement statement = connection.prepareStatement(SQL);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String name = rows.getString("TABLE_NAME");
                if (!"BASE TABLE".equals(rows.getString("TABLE_TYPE"))) unsupportedObject = true;
                Set<String> columns = tables.computeIfAbsent(name, ignored -> new LinkedHashSet<>());
                String column = rows.getString("COLUMN_NAME");
                if (column != null) columns.add(column);
            }
        }
        Report result = classify(tables);
        return unsupportedObject ? new Report(Kind.UNKNOWN, result.historyPresent(), result.featuredColumnPresent()) : result;
    }

    static Report classify(Map<String, Set<String>> tables) {
        boolean history = tables.containsKey("flyway_schema_history");
        boolean featured = tables.getOrDefault("articles", Set.of()).contains("last_featured_at");
        Set<String> names = new LinkedHashSet<>(tables.keySet());
        names.remove("flyway_schema_history");
        if (names.isEmpty()) return new Report(history ? Kind.UNKNOWN : Kind.EMPTY, history, featured);
        Set<String> users = tables.getOrDefault("users", Set.of());
        if (names.equals(MONOLITH_TABLES) && users.contains("password_hash") && !users.contains("password")) {
            return new Report(Kind.MONOLITH, history, featured);
        }
        if (names.equals(LEGACY_TABLES) && !users.contains("password_hash")
                && !users.contains("session_version")
                && LEGACY_KEYS.entrySet().stream().allMatch(e -> tables.get(e.getKey()).containsAll(e.getValue()))) {
            return new Report(Kind.LEGACY_MICROSERVICE, history, featured);
        }
        return new Report(Kind.UNKNOWN, history, featured);
    }
}
