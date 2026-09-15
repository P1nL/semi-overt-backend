package com.platform.content.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "platform.ai.polish")
public class ArticlePolishProperties {
    private String baseUrl = "https://api.deepseek.com";
    /** Legacy full chat/completions endpoint, retained for existing local configuration. */
    private String endpoint = "";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private String proxyUrl = "";
    private int timeoutSeconds = 45;
    private int maxTokens = 8192;
}
