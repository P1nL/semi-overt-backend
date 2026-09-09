package com.platform.migration;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaPreflightTest {
    private Map<String, Set<String>> legacy() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__baseline_schema.sql")) {
            assertNotNull(stream);
            sql = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS (\\w+) \\((.*?)\\) ENGINE", Pattern.DOTALL).matcher(sql);
        while (matcher.find()) {
            Set<String> columns = new LinkedHashSet<>();
            for (String line : matcher.group(2).split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isBlank() && !trimmed.startsWith("PRIMARY") && !trimmed.startsWith("UNIQUE") && !trimmed.startsWith("KEY"))
                    columns.add(trimmed.split("\\s+")[0]);
            }
            tables.put(matcher.group(1), columns);
        }
        assertEquals(8, tables.size());
        return tables;
    }

    @Test void emptySchemaAllowed() {
        var report = SchemaPreflight.classify(Map.of());
        assertEquals(SchemaPreflight.Kind.EMPTY, report.kind());
        assertDoesNotThrow(report::requireMigrationAllowed);
    }
    @Test void historyOnlyIsNotAnEmptyDatabase() {
        var report = SchemaPreflight.classify(Map.of("flyway_schema_history", Set.of("version")));
        assertEquals(SchemaPreflight.Kind.UNKNOWN, report.kind());
        assertThrows(IllegalStateException.class, report::requireMigrationAllowed);
    }
    @Test void legacyWithoutHistoryRequiresExplicitOnboarding() throws Exception {
        var report = SchemaPreflight.classify(legacy());
        assertEquals(SchemaPreflight.Kind.LEGACY_MICROSERVICE, report.kind());
        assertThrows(IllegalStateException.class, report::requireMigrationAllowed);
    }
    @Test void legacyHistoryAllowsNextValidationNotAutomaticTrust() throws Exception {
        var tables = legacy(); tables.put("flyway_schema_history", Set.of("version"));
        assertDoesNotThrow(SchemaPreflight.classify(tables)::requireMigrationAllowed);
    }
    @Test void unknownExtraTableBlocksEvenWithHistory() throws Exception {
        var tables = legacy(); tables.put("flyway_schema_history", Set.of("version"));
        tables.put("unrelated", Set.of("id"));
        assertThrows(IllegalStateException.class, SchemaPreflight.classify(tables)::requireMigrationAllowed);
    }
    @Test void missingColumnIsUnknown() throws Exception {
        var tables = legacy(); tables.get("review_logs").remove("from_status");
        assertEquals(SchemaPreflight.Kind.UNKNOWN, SchemaPreflight.classify(tables).kind());
    }
    @Test void mixedPasswordModelsAreUnknown() throws Exception {
        var tables = legacy(); tables.get("users").add("password_hash");
        assertEquals(SchemaPreflight.Kind.UNKNOWN, SchemaPreflight.classify(tables).kind());
    }
    @Test void monolithNeverRunsLegacyMigrations() {
        Map<String, Set<String>> tables = new HashMap<>();
        for (String name : List.of("users","articles","review_logs","review_tasks","notifications",
                "rate_limit_buckets","auth_device_sessions","auth_refresh_tokens","password_reset_tokens",
                "email_verification_codes","home_article_exposures")) tables.put(name, Set.of("id"));
        tables.put("users", Set.of("id", "password_hash"));
        tables.put("flyway_schema_history", Set.of("version"));
        var report = SchemaPreflight.classify(tables);
        assertEquals(SchemaPreflight.Kind.MONOLITH, report.kind());
        assertThrows(IllegalStateException.class, report::requireMigrationAllowed);
    }
    @Test void missingSelectedDatabaseStopsBeforeMetadataRead() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> SchemaPreflight.inspect(connection));
        verify(connection, never()).prepareStatement(SchemaPreflight.SQL);
        verify(statement).close(); verify(rows).close();
    }
    @Test void existingViewsCannotMasqueradeAsEmpty() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement database = mock(PreparedStatement.class);
        PreparedStatement metadata = mock(PreparedStatement.class);
        ResultSet selected = mock(ResultSet.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(database);
        when(database.executeQuery()).thenReturn(selected);
        when(selected.next()).thenReturn(true);
        when(selected.getString(1)).thenReturn("isolated_test");
        when(connection.prepareStatement(SchemaPreflight.SQL)).thenReturn(metadata);
        when(metadata.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("TABLE_NAME")).thenReturn("users");
        when(rows.getString("TABLE_TYPE")).thenReturn("VIEW");
        when(rows.getString("COLUMN_NAME")).thenReturn("id");
        var report = SchemaPreflight.inspect(connection);
        assertEquals(SchemaPreflight.Kind.UNKNOWN, report.kind());
        assertThrows(IllegalStateException.class, report::requireMigrationAllowed);
        verify(connection, never()).createStatement();
        verify(metadata).close(); verify(rows).close();
    }
    @Test void metadataQueryIsScopedAndReadOnly() {
        assertTrue(SchemaPreflight.SQL.startsWith("SELECT "));
        assertTrue(SchemaPreflight.SQL.contains("t.TABLE_SCHEMA=DATABASE()"));
    }
}
