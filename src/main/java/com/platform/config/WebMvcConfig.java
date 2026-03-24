package com.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置
 * 1. 统一跨域策略（开发环境允许前端 dev server）
 * 2. 本地文件存储目录映射（/static/uploads/** → 本地磁盘）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 文件实际存储根目录 */
    @Value("${storage.upload-path}")
    private String uploadPath;

    /** 对外访问的 URL 前缀 */
    @Value("${storage.access-prefix}")
    private String accessPrefix;

    /**
     * 跨域配置
     * 生产环境请将 allowedOrigins 改为实际前端域名，不要用 *
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 开发阶段允许 Vue 开发服务器（5173 为 Vite 默认端口）
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:3000",
                        "http://127.0.0.1:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                // 允许前端读取 New-Token 响应头（Token 刷新时使用）
                .exposedHeaders("New-Token", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 本地文件存储目录映射
     * 访问 /static/uploads/xxx 实际读取 uploadPath/xxx
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 确保路径以 file: 开头，以 / 结尾
        Path uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
        String resourceLocation = uploadRoot.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler(accessPrefix + "/**")
                .addResourceLocations(resourceLocation);
    }
}
