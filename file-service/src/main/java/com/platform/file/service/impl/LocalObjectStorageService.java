package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.kernel.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private final StorageConfig storageConfig;

    public LocalObjectStorageService(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    @Override
    public String store(String objectKey, byte[] bytes, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Uploaded file is empty");
        }
        Path uploadRoot = uploadRoot();
        Path physicalPath = safePath(uploadRoot, objectKey);
        if (physicalPath == null || containsSymbolicLink(uploadRoot, physicalPath)) {
            throw BusinessException.serverError("Invalid storage path");
        }

        Files.createDirectories(physicalPath.getParent());
        try {
            // Generated UUID keys are unique. Refuse a collision instead of
            // replacing an object that may already be referenced elsewhere.
            Files.write(physicalPath, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ex) {
            throw BusinessException.serverError("Generated storage key already exists");
        }
        return normalizeAccessPrefix() + "/" + objectKey;
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            Path root = uploadRoot();
            Path physicalPath = safePath(root, objectKey);
            if (physicalPath == null || containsSymbolicLink(root, physicalPath)) {
                log.warn("Skipping delete for unsafe local object key");
                return;
            }
            Files.deleteIfExists(physicalPath);
        } catch (IOException ex) {
            log.warn("Failed to delete local object: {}", ex.getMessage());
        }
    }

    @Override
    public void validateReadiness() {
        Path root = uploadRoot();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw BusinessException.serverError("Local upload path is not writable: " + root);
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw BusinessException.serverError("Local upload path is not writable: " + root);
        }
        normalizeAccessPrefix();
    }

    Path uploadRoot() {
        if (storageConfig.getUploadPath() == null || storageConfig.getUploadPath().isBlank()) {
            throw BusinessException.serverError("storage.upload-path must not be blank");
        }
        return Paths.get(storageConfig.getUploadPath()).toAbsolutePath().normalize();
    }

    private String normalizeAccessPrefix() {
        String prefix = storageConfig.getAccessPrefix();
        if (prefix == null || prefix.isBlank() || !prefix.startsWith("/")
                || prefix.contains("..") || prefix.contains("\\") || prefix.contains("?")
                || prefix.contains("#")) {
            throw BusinessException.serverError("storage.access-prefix must be a safe path");
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    private Path safePath(Path root, String objectKey) {
        if (objectKey == null || objectKey.isBlank()
                || objectKey.startsWith("/") || objectKey.startsWith("\\")
                || objectKey.contains("\\") || objectKey.indexOf('\0') >= 0) {
            return null;
        }
        Path candidate = root.resolve(objectKey).normalize();
        return candidate.startsWith(root) ? candidate : null;
    }

    private boolean containsSymbolicLink(Path root, Path candidate) {
        Path current = candidate;
        while (current != null && current.startsWith(root) && !current.equals(root)) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}