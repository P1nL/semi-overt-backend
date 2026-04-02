package com.platform.file.health;

import com.platform.file.service.ObjectStorageService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("fileStorage")
public class FileStorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageService objectStorageService;

    public FileStorageHealthIndicator(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

    @Override
    public Health health() {
        try {
            objectStorageService.validateReadiness();
            return Health.up().build();
        } catch (RuntimeException ex) {
            return Health.down(ex).withDetail("message", ex.getMessage()).build();
        }
    }
}
