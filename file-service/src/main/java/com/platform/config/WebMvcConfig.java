package com.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * file-service 的 Web MVC 配置。
 * 负责开放开发环境跨域，并把本地上传目录映射为可访问的静态资源路径。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 文件物理存储根目录。 */
    @Value("${storage.upload-path}")
    private String uploadPath;

    /** 对外访问的 URL 前缀。 */
    @Value("${storage.access-prefix}")
    private String accessPrefix;

    /**
     * 配置跨域规则。
     * 当前仅面向本地开发域名开放。
     */
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
     * 把本地上传目录映射为静态资源路径。
     */
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
