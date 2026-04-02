package com.platform.file.config;

import com.platform.web.support.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {

    @Test
    void addResourceHandlersShouldRegisterLocalUploadMappingOutsideProd() {
        StorageConfig storageConfig = createStorageConfig();
        WebMvcConfig config = new WebMvcConfig(storageConfig, createCorsProperties(), new MockEnvironment());
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                new StaticWebApplicationContext(),
                new MockServletContext()
        );

        config.addResourceHandlers(registry);

        assertThat(registry.hasMappingForPattern("/static/uploads/**")).isTrue();
    }

    @Test
    void addResourceHandlersShouldSkipLocalUploadMappingInProd() {
        StorageConfig storageConfig = createStorageConfig();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        WebMvcConfig config = new WebMvcConfig(
                storageConfig,
                createCorsProperties(),
                environment
        );
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                new StaticWebApplicationContext(),
                new MockServletContext()
        );

        config.addResourceHandlers(registry);

        assertThat(registry.hasMappingForPattern("/static/uploads/**")).isFalse();
    }

    @Test
    void addResourceHandlersShouldSkipLocalUploadMappingWhenStorageTypeIsOss() {
        StorageConfig storageConfig = createStorageConfig();
        storageConfig.setType("oss");
        WebMvcConfig config = new WebMvcConfig(storageConfig, createCorsProperties(), new MockEnvironment());
        ResourceHandlerRegistry registry = new ResourceHandlerRegistry(
                new StaticWebApplicationContext(),
                new MockServletContext()
        );

        config.addResourceHandlers(registry);

        assertThat(registry.hasMappingForPattern("/static/uploads/**")).isFalse();
    }

    private StorageConfig createStorageConfig() {
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setType("local");
        storageConfig.setUploadPath("E:/nowdata/app/uploads");
        storageConfig.setAccessPrefix("/static/uploads");
        return storageConfig;
    }

    private CorsProperties createCorsProperties() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        return properties;
    }
}
