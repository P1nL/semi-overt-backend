package com.platform.file.config;

import com.platform.web.support.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageConfig storageConfig;
    private final CorsProperties corsProperties;

    public WebMvcConfig(StorageConfig storageConfig, CorsProperties corsProperties) {
        this.storageConfig = storageConfig;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("New-Token", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(storageConfig.getUploadPath()).toAbsolutePath().normalize();
        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation += "/";
        }
        registry.addResourceHandler(normalizeAccessPrefix() + "/**")
                .addResourceLocations(resourceLocation)
                .setCachePeriod(2592000)
                .resourceChain(true);
    }

    private String normalizeAccessPrefix() {
        String prefix = storageConfig.getAccessPrefix();
        if (prefix == null || prefix.isBlank() || !prefix.startsWith("/")
                || prefix.contains("..") || prefix.contains("\\")
                || prefix.contains("?") || prefix.contains("#")) {
            throw new IllegalStateException("storage.access-prefix must be a safe path");
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}