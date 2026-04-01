package com.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalArchitectureTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final List<String> SERVICES = List.of(
            "gateway-service",
            "auth-service",
            "content-service",
            "review-service",
            "search-service",
            "file-service",
            "notification-service"
    );

    @Test
    void rootPomUsesFinalModuleSet() throws IOException {
        String pom = Files.readString(ROOT.resolve("pom.xml"));

        assertFalse(pom.contains("<module>common</module>"),
                "Final architecture must not keep the common module in the root build");
        for (String module : List.of(
                "platform-kernel",
                "platform-web-support",
                "platform-events",
                "auth-contract",
                "content-contract",
                "review-contract",
                "architecture-tests")) {
            assertTrue(pom.contains("<module>" + module + "</module>"),
                    () -> "Root pom must keep module " + module + " registered");
        }
        assertFalse(Files.exists(ROOT.resolve("common")),
                "Final architecture must remove the common compatibility module directory");
    }

    @Test
    void servicePomsDoNotDependOnCommon() throws IOException {
        for (String service : SERVICES) {
            Path pomPath = ROOT.resolve(service).resolve("pom.xml");
            String pom = Files.readString(pomPath);
            assertFalse(pom.contains("<artifactId>common</artifactId>"),
                    () -> "Service pom must not depend on common: " + pomPath);
        }
    }

    @Test
    void onlyContractModulesDeclareFeignClients() throws IOException {
        for (Path javaFile : javaFilesUnder(ROOT)) {
            if (!javaFile.toString().contains("src/main/java")) {
                continue;
            }
            String content = Files.readString(javaFile);
            if (content.contains("@FeignClient")) {
                assertTrue(javaFile.toString().contains("-contract"),
                        () -> "@FeignClient must live in contract modules only: " + javaFile);
            }
        }
    }

    @Test
    void eventInfrastructureLivesOnlyInPlatformEvents() throws IOException {
        List<String> eventInfrastructureFiles = List.of(
                "PlatformEventsConfig.java",
                "RabbitEventConfig.java",
                "EventConsumeLog.java",
                "EventOutbox.java",
                "EventConsumeStatus.java",
                "EventOutboxStatus.java",
                "EventConsumeLogMapper.java",
                "EventOutboxMapper.java",
                "EventConsumeService.java",
                "EventOutboxService.java",
                "EventListenerExecutor.java",
                "OutboxPublisherSupport.java",
                "RabbitRetrySupport.java"
        );

        for (Path javaFile : javaFilesUnder(ROOT)) {
            if (!javaFile.toString().contains("src/main/java")) {
                continue;
            }
            if (eventInfrastructureFiles.contains(javaFile.getFileName().toString())) {
                assertTrue(javaFile.startsWith(ROOT.resolve("platform-events")),
                        () -> "Event infrastructure must live under platform-events only: " + javaFile);
            }
        }
    }

    @Test
    void servicesDoNotReferenceLegacySharedRoots() throws IOException {
        for (String service : SERVICES) {
            for (Path javaFile : javaFilesUnder(ROOT.resolve(service).resolve("src"))) {
                String content = Files.readString(javaFile);
                for (String legacyPrefix : List.of(
                        "com.platform.common.",
                        "com.platform.util.",
                        "com.platform.enums.",
                        "com.platform.exception."
                )) {
                    assertFalse(content.contains(legacyPrefix),
                            () -> "Service source must not reference legacy shared root " + legacyPrefix + ": " + javaFile);
                }
            }
        }
    }

    @Test
    void serviceEntrypointsUseExplicitBoundaries() throws IOException {
        for (String entrypoint : List.of(
                "gateway-service/src/main/java/com/platform/gateway/GatewayServiceApplication.java",
                "auth-service/src/main/java/com/platform/auth/AuthServiceApplication.java",
                "content-service/src/main/java/com/platform/content/ContentServiceApplication.java",
                "review-service/src/main/java/com/platform/review/ReviewServiceApplication.java",
                "search-service/src/main/java/com/platform/search/SearchServiceApplication.java",
                "file-service/src/main/java/com/platform/file/FileServiceApplication.java",
                "notification-service/src/main/java/com/platform/notification/NotificationServiceApplication.java"
        )) {
            Path path = ROOT.resolve(entrypoint);
            assertTrue(Files.exists(path), () -> "Expected service entrypoint to exist: " + entrypoint);

            String source = Files.readString(path);
            assertFalse(source.contains("scanBasePackages"),
                    () -> "Service entrypoint must not use wide component scanning: " + path);
            assertFalse(source.contains("@EnableFeignClients(basePackages"),
                    () -> "Service entrypoint must not use wide Feign scanning: " + path);
            assertFalse(source.contains("basePackageClasses"),
                    () -> "Service entrypoint must not use package-class Feign scanning: " + path);
            if (source.contains("@EnableFeignClients(")) {
                assertTrue(source.contains("clients ="),
                        () -> "Service entrypoint Feign wiring must use explicit clients declarations: " + path);
            }
        }
    }

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\.idea\\"))
                    .filter(path -> !path.toString().contains("/.idea/"))
                    .toList();
        }
    }
}
