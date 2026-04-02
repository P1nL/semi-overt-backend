package com.platform.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文件存储配置，统一承载本地存储与 OSS 存储的运行参数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    /**
     * 当前启用的存储类型，例如 `local` 或 `oss`。
     */
    private String type = "local";

    /**
     * 本地存储根目录。
     */
    private String uploadPath;

    /**
     * 对外访问前缀。
     */
    private String accessPrefix;

    /**
     * 允许上传的文件类型白名单。
     */
    private List<String> allowedTypes;

    /**
     * 单个文件允许的最大大小。
     */
    private long maxFileSize;

    /**
     * OSS 相关配置。
     */
    private final Oss oss = new Oss();

    /**
     * OSS 存储配置。
     */
    @Data
    public static class Oss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        private String publicBaseUrl;
    }
}
