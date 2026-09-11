package com.platform.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Contract-complementary ownership guards: not substitutes for the real database tests. */
class S4DomainBoundaryTest {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    @Test void userAggregationDoesNotReadContentTablesDirectly() throws Exception {
        Pattern contentRead=Pattern.compile("(?is)\\b(?:from|join)\\s+`?(?:articles|home_article_exposures)`?\\b");
        for(Path file:sourceFiles("auth-service")) {
            assertFalse(contentRead.matcher(Files.readString(file)).find(),()->"Auth must call content contract: "+file);
        }
    }
    @Test void fileAndNotificationCannotIntroduceCrossDomainDatabaseReads() throws Exception {
        Pattern crossRead=Pattern.compile("(?is)\\b(?:from|join)\\s+`?(?:users|articles|auth_device_sessions|auth_refresh_tokens)`?\\b");
        for(String module:List.of("file-service","notification-service")) {
            for(Path file:sourceFiles(module)) {
                assertFalse(crossRead.matcher(Files.readString(file)).find(),()->"Cross-domain SQL instead of contract: "+file);
            }
        }
    }
    @Test void searchRemainsReadOnlyWithoutInventingIndependentIndexInfrastructure() throws Exception {
        Pattern writes=Pattern.compile("(?is)\\b(?:update|insert\\s+into|delete\\s+from)\\s+`?(?:articles|users|home_article_exposures)`?\\b");
        for(Path file:sourceFiles("search-service")) {
            assertFalse(writes.matcher(Files.readString(file)).find(),()->"Search must remain read-only: "+file);
        }
        String pom=Files.readString(ROOT.resolve("search-service/pom.xml"));
        assertFalse(pom.contains("spring-boot-starter-data-elasticsearch"),"Independent Elasticsearch indexing is outside S4");
    }
    private List<Path> sourceFiles(String module) throws Exception {
        try(var paths=Files.walk(ROOT.resolve(module).resolve("src/main"))) {
            return paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")||p.toString().endsWith(".xml")).toList();
        }
    }
}
