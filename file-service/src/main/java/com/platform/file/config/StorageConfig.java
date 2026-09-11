package com.platform.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** File storage configuration; local storage remains the default. */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {
    private String type = "local";
    private String uploadPath;
    private String accessPrefix;
    private List<String> allowedTypes;
    private long maxFileSize;
    private int maxImageDimension = 8192;
    private long maxImagePixels = 24_000_000L;
    private int maxConcurrentImageDecodes = 2;
    private final Oss oss = new Oss();
    private final Cloudinary cloudinary = new Cloudinary();

    @Data
    public static class Oss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        private String publicBaseUrl;

        @Override
        public String toString() {
            return "Oss[bucket=" + bucket + ", credentials=REDACTED]";
        }
    }

    @Data
    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
        private String apiBaseUrl = "https://api.cloudinary.com";
        private String deliveryBaseUrl;

        @Override
        public String toString() {
            return "Cloudinary[cloudName=" + cloudName + ", credentials=REDACTED]";
        }
    }
}