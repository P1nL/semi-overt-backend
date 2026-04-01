package com.platform.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

        private String uploadPath;

        private String accessPrefix;

        private List<String> allowedTypes;

    /** 閸楁洘鏋冩禒璺恒亣鐏忓繋绗傞梽鎰剁礉閸楁洑缍呯€涙濡妴?*/
    private long maxFileSize;
}

