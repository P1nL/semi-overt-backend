package com.platform.migration;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
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

    public static void main(String[] args) {
        SpringApplication.run(DbMigrationApplication.class, args);
    }

    @Override
    public void run(String... args) {
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, dbUsername, dbPassword)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();
        log.info("Flyway migration finished. Initial schema version={}, target schema version={}, migrations executed={}",
                result.initialSchemaVersion,
                result.targetSchemaVersion,
                result.migrationsExecuted);
    }
}
