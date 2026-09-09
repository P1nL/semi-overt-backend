package com.platform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(
            @Value("${platform.turnstile.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${platform.turnstile.read-timeout-ms:5000}") int readTimeoutMs) {
        if (connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("Turnstile HTTP timeouts must be positive");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
