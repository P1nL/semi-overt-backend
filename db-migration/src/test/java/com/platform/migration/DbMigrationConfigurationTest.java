package com.platform.migration;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DbMigrationConfigurationTest {

    @Test
    void disablesBaselineOnMigrateAndUsesMigrationLocation() {
        FluentConfiguration configuration = DbMigrationApplication.createConfiguration(
                "jdbc:mysql://127.0.0.1:1/unused",
                "unused",
                "unused"
        );

        assertFalse(configuration.isBaselineOnMigrate());
        assertArrayEquals(
                new String[]{"classpath:db/migration"},
                Arrays.stream(configuration.getLocations())
                        .map(location -> location.getDescriptor())
                        .toArray(String[]::new)
        );
    }
}
