package com.platform.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.file.service.OssClientFactory;
import com.platform.kernel.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssObjectStorageService implements ObjectStorageService {

    private final StorageConfig storageConfig;
    private final OssClientFactory ossClientFactory;

    public OssObjectStorageService(StorageConfig storageConfig, OssClientFactory ossClientFactory) {
        this.storageConfig = storageConfig;
        this.ossClientFactory = ossClientFactory;
    }

    @Override
    public String store(String objectKey, byte[] bytes, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Uploaded file is empty");
        }
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        validateConfig(ossConfig);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }

        OSS client = buildClient();
        try {
            client.putObject(ossConfig.getBucket(), objectKey,
                    new java.io.ByteArrayInputStream(bytes), metadata);
        } finally {
            client.shutdown();
        }
        return normalizeBaseUrl(ossConfig.getPublicBaseUrl()) + "/" + objectKey;
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        validateConfig(ossConfig);
        OSS client = buildClient();
        try {
            client.deleteObject(ossConfig.getBucket(), objectKey);
        } catch (Exception ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("NoSuchKey")) {
                throw ex;
            }
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void validateReadiness() {
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        validateConfig(ossConfig);
        OSS client = buildClient();
        try {
            if (!client.doesBucketExist(ossConfig.getBucket())) {
                throw BusinessException.serverError("OSS bucket does not exist: " + ossConfig.getBucket());
            }
        } finally {
            client.shutdown();
        }
    }

    private OSS buildClient() {
        return ossClientFactory.createClient(storageConfig.getOss());
    }

    private void validateConfig(StorageConfig.Oss config) {
        if (isBlank(config.getEndpoint()) || isBlank(config.getBucket())
                || isBlank(config.getAccessKeyId()) || isBlank(config.getAccessKeySecret())
                || isBlank(config.getPublicBaseUrl())) {
            throw BusinessException.serverError("OSS storage config is incomplete");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}