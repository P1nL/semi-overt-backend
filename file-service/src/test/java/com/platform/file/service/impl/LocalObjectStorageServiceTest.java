package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import com.platform.kernel.exception.BusinessException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesUnderRootAndReturnsStaticAccessUrl() throws Exception {
        LocalObjectStorageService service = service();
        service.validateReadiness();

        String url = service.store("2026/09/11/object.png", "payload".getBytes(StandardCharsets.UTF_8), "image/png");

        assertThat(url).isEqualTo("/static/uploads/2026/09/11/object.png");
        assertThat(Files.readString(tempDir.resolve("2026/09/11/object.png"))).isEqualTo("payload");
    }

    @Test
    void refusesCreateNewCollisionWithoutReplacingExistingBytes() throws Exception {
        LocalObjectStorageService service = service();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        service.store("same/object.png", original, "image/png");

        assertThatThrownBy(() -> service.store("same/object.png", "replacement".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        assertThat(Files.readAllBytes(tempDir.resolve("same/object.png"))).isEqualTo(original);
    }

    @Test
    void rejectsTraversalAndNeverDeletesOutsideRoot() throws Exception {
        Path outside = tempDir.resolveSibling("file-service-outside-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "keep");
        LocalObjectStorageService service = service();

        assertThatThrownBy(() -> service.store("../" + outside.getFileName(), new byte[]{1}, "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid storage path");
        service.delete("../" + outside.getFileName());
        assertThat(Files.readString(outside)).isEqualTo("keep");
        Files.deleteIfExists(outside);
    }

    @Test
    void rejectsSymlinkedDirectoryEscapeWhenPlatformAllowsCreatingSymlinks() throws Exception {
        Path outside = tempDir.resolveSibling("file-service-symlink-outside-" + System.nanoTime());
        Path link = tempDir.resolve("linked");
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(link, outside);
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + ex.getMessage());
        }

        LocalObjectStorageService service = service();
        assertThatThrownBy(() -> service.store("linked/escaped.png", new byte[]{1}, "image/png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid storage path");
        assertThat(Files.exists(outside.resolve("escaped.png"))).isFalse();
    }

    private LocalObjectStorageService service() {
        StorageConfig config = new StorageConfig();
        config.setUploadPath(tempDir.toString());
        config.setAccessPrefix("/static/uploads");
        return new LocalObjectStorageService(config);
    }
}