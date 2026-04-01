package com.platform.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        @Value("${storage.upload-path}")
    private String uploadPath;

        @Value("${storage.access-prefix}")
    private String accessPrefix;

        @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:3000",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("New-Token", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 閹跺﹥婀伴崷棰佺瑐娴肩姷娲拌ぐ鏇熸Ё鐏忓嫪璐熼棃娆愨偓浣界カ濠ф劘鐭惧鍕┾偓?     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler(accessPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}

