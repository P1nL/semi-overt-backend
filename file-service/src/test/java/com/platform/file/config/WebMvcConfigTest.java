package com.platform.file.config;

import com.platform.web.support.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcConfigTest {
    @Test
    void registersHistoricalLocalStaticMapping() {
        ResourceHandlerRegistry registry = registry();
        new WebMvcConfig(config(), cors()).addResourceHandlers(registry);
        assertThat(registry.hasMappingForPattern("/static/uploads/**")).isTrue();
    }

    @Test
    void keepsHistoricalStaticMappingWhenExternalProviderIsSelected() {
        StorageConfig config = config();
        config.setType("cloudinary");
        ResourceHandlerRegistry registry = registry();
        new WebMvcConfig(config, cors()).addResourceHandlers(registry);
        assertThat(registry.hasMappingForPattern("/static/uploads/**")).isTrue();
    }

    private ResourceHandlerRegistry registry() {
        return new ResourceHandlerRegistry(new StaticWebApplicationContext(), new MockServletContext());
    }

    private StorageConfig config() {
        StorageConfig config = new StorageConfig();
        config.setType("local");
        config.setUploadPath("E:/nowdata/app/uploads");
        config.setAccessPrefix("/static/uploads");
        return config;
    }

    private CorsProperties cors() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:5173"));
        return properties;
    }
}