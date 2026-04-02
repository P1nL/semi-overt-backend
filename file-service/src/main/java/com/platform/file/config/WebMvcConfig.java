package com.platform.file.config;

import com.platform.web.support.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageConfig storageConfig;
    private final CorsProperties corsProperties;
    private final Environment environment;

    public WebMvcConfig(StorageConfig storageConfig, CorsProperties corsProperties, Environment environment) {
        this.storageConfig = storageConfig;
        this.corsProperties = corsProperties;
        this.environment = environment;
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
        if (environment.matchesProfiles("prod") || !"local".equalsIgnoreCase(storageConfig.getType())) {
            return;
        }

        Path uploadRoot = Paths.get(storageConfig.getUploadPath()).toAbsolutePath().normalize();
        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler(storageConfig.getAccessPrefix() + "/**")
                .addResourceLocations(resourceLocation);
    }
}
