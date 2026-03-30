package com.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 本地文件存储配置。
 * 对应 `application.yml` 中的 `storage` 前缀。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    /** 文件物理存储目录。 */
    private String uploadPath;

    /** 对外访问 URL 前缀。 */
    private String accessPrefix;

    /** 允许上传的 MIME 类型。 */
    private List<String> allowedTypes;

    /** 单文件大小上限，单位字节。 */
    private long maxFileSize;
}
