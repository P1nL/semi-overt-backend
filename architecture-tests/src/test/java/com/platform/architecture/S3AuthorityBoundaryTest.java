package com.platform.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Narrow write-authority regressions, complementary to runtime concurrency tests. */
class S3AuthorityBoundaryTest {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static final Pattern ARTICLE_WRITE=Pattern.compile("(?is)\\b(?:update|insert\\s+into|delete\\s+from)\\s+`?articles`?\\b");
    private static final Pattern AUTH_WRITE=Pattern.compile("(?is)\\b(?:update|insert\\s+into|delete\\s+from)\\s+`?users`?\\b");
    @Test void nonContentServicesCannotWriteArticlesAndNonAuthServicesCannotWriteUsers() throws Exception {
        for(String module:List.of("auth-service","review-service","search-service","file-service","notification-service","platform-events")) {
            try(var paths=Files.walk(ROOT.resolve(module).resolve("src/main"))) {
                for(Path file:paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")||p.toString().endsWith(".xml")).toList()) {
                    String source=Files.readString(file);
                    assertFalse(ARTICLE_WRITE.matcher(source).find(),()->"Article write outside content: "+file);
                    if(!module.equals("auth-service"))assertFalse(AUTH_WRITE.matcher(source).find(),()->"User write outside auth: "+file);
                }
            }
        }
    }
    @Test void oldUnversionedDraftFlushSchedulerCannotReturn() {
        assertFalse(Files.exists(ROOT.resolve("content-service/src/main/java/com/platform/content/task/DraftFlushTask.java")),
                "Unversioned Redis draft flush must not be scheduled");
    }
    @Test void additiveS3MigrationsAgreeAcrossApprovedSourceRoutes() throws Exception {
        assertEquals(Files.readString(ROOT.resolve("db-migration/src/main/resources/db/migration/V4__s3_state_consistency.sql")),
                Files.readString(ROOT.resolve("db-migration/src/main/resources/db/monolith/V4__s3_state_consistency.sql")));
    }
}
