package com.platform.file.config;

import com.platform.file.service.impl.CloudinaryObjectStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Creates the optional Cloudinary provider only when explicitly selected. */
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "cloudinary")
public class CloudinaryStorageConfig {
    @Bean
    CloudinaryObjectStorageService cloudinaryObjectStorageService(StorageConfig storageConfig) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new CloudinaryObjectStorageService(
                RestClient.builder().requestFactory(factory), storageConfig);
    }
}