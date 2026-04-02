package com.platform.file.health;

import com.platform.file.service.ObjectStorageService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class FileStorageHealthIndicatorTest {

    @Test
    void healthShouldBeUpWhenStorageIsReady() {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        FileStorageHealthIndicator indicator = new FileStorageHealthIndicator(objectStorageService);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void healthShouldBeDownWhenStorageValidationFails() {
        ObjectStorageService objectStorageService = mock(ObjectStorageService.class);
        doThrow(new IllegalStateException("OSS unavailable")).when(objectStorageService).validateReadiness();
        FileStorageHealthIndicator indicator = new FileStorageHealthIndicator(objectStorageService);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(indicator.health().getDetails()).containsEntry("message", "OSS unavailable");
    }
}
