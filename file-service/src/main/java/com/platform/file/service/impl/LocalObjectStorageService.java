package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.kernel.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private final StorageConfig storageConfig;

    public LocalObjectStorageService(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    @Override
    public String store(String objectKey, MultipartFile file) throws IOException {
        Path uploadRoot = Paths.get(storageConfig.getUploadPath()).toAbsolutePath().normalize();
        Path physicalPath = uploadRoot.resolve(objectKey).normalize();
        if (!physicalPath.startsWith(uploadRoot)) {
            throw BusinessException.serverError("Invalid storage path");
        }

        Files.createDirectories(physicalPath.getParent());
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, physicalPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return normalizeBaseUrl(storageConfig.getAccessPrefix()) + "/" + objectKey;
    }

    @Override
    public void validateReadiness() {
        Path uploadRoot = Paths.get(storageConfig.getUploadPath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw BusinessException.serverError("Local upload path is not writable: " + uploadRoot);
        }
        if (!Files.isWritable(uploadRoot)) {
            throw BusinessException.serverError("Local upload path is not writable: " + uploadRoot);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw BusinessException.serverError("storage.access-prefix must not be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
