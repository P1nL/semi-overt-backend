package com.platform.file.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.file.service.OssClientFactory;
import com.platform.kernel.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    public String store(String objectKey, MultipartFile file) throws IOException {
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        validateConfig(ossConfig);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            metadata.setContentType(file.getContentType());
        }

        OSS ossClient = buildClient();
        try {
            ossClient.putObject(ossConfig.getBucket(), objectKey, file.getInputStream(), metadata);
        } finally {
            ossClient.shutdown();
        }

        return normalizeBaseUrl(ossConfig.getPublicBaseUrl()) + "/" + objectKey;
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        OSS ossClient = buildClient();
        try {
            ossClient.deleteObject(ossConfig.getBucket(), objectKey);
        } catch (Exception e) {
            // 对象不存在时静默忽略；其余异常仅记录不抛出，不影响上传主流程
            if (e.getMessage() != null && e.getMessage().contains("NoSuchKey")) {
                return;
            }
            throw e;
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public void validateReadiness() {
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        validateConfig(ossConfig);
        OSS ossClient = buildClient();
        try {
            if (!ossClient.doesBucketExist(ossConfig.getBucket())) {
                throw BusinessException.serverError("OSS bucket does not exist: " + ossConfig.getBucket());
            }
        } finally {
            ossClient.shutdown();
        }
    }

    private OSS buildClient() {
        StorageConfig.Oss ossConfig = storageConfig.getOss();
        return ossClientFactory.createClient(ossConfig);
    }

    private void validateConfig(StorageConfig.Oss ossConfig) {
        if (isBlank(ossConfig.getEndpoint())
                || isBlank(ossConfig.getBucket())
                || isBlank(ossConfig.getAccessKeyId())
                || isBlank(ossConfig.getAccessKeySecret())
                || isBlank(ossConfig.getPublicBaseUrl())) {
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
