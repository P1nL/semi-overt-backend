package com.platform.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class DbMigrationApplication implements CommandLineRunner {

    @Value("${DB_URL}")
    private String dbUrl;

    @Value("${DB_USERNAME}")
    private String dbUsername;

    @Value("${DB_PASSWORD}")
    private String dbPassword;

    @Value("${MIGRATION_MODE:AUTO}")
    private String migrationMode;

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationApplication.class, args);
    }

    static FluentConfiguration createConfiguration(String url, String user, String password) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .baselineOnMigrate(false)
                .locations("classpath:db/migration");
    }

    @Override
    public void run(String... args) throws java.sql.SQLException {
        MigrateResult result = MigrationRunner.migrate(
                createConfiguration(dbUrl, dbUsername, dbPassword).getDataSource(),
                MigrationRunner.Mode.valueOf(migrationMode));
        log.info("Flyway migration finished. Initial schema version={}, target schema version={}, migrations executed={}",
                result.initialSchemaVersion,
                result.targetSchemaVersion,
                result.migrationsExecuted);
    }
}
