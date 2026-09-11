package com.platform.file.service.impl;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.file.support.TestImageFixtures;
import com.platform.kernel.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class UploadServiceImplTest {
    private ObjectStorageService storage;
    private UploadServiceImpl service;

    @BeforeEach
    void setUp() {
        storage = mock(ObjectStorageService.class);
        StorageConfig config = new StorageConfig();
        config.setMaxFileSize(5 * 1024 * 1024L);
        service = new UploadServiceImpl(storage, new ImageUploadSupport(config));
    }

    @Test
    void validatesImageBeforeProviderAndReturnsMetadata() throws Exception {
        byte[] png = TestImageFixtures.png(Color.decode("#336699"));
        when(storage.store(anyString(), any(byte[].class), anyString()))
                .thenReturn("/static/uploads/2026/09/11/object.png");

        UploadResp response = service.upload(
                new MockMultipartFile("file", "cover.png", "image/png", png),
                "COVER", 9L, "/static/uploads/victim.png");

        assertThat(response.getUrl()).isEqualTo("/static/uploads/2026/09/11/object.png");
        assertThat(response.getWidth()).isEqualTo(2);
        assertThat(response.getHeight()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo((long) png.length);
        assertThat(response.getDominantColor()).isEqualTo("#336699");
        verify(storage).store(anyString(), any(byte[].class), anyString());
        verify(storage, never()).delete(anyString());
    }

    @Test
    void rejectsUnsupportedBusinessType() {
        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "x.png", "image/png", new byte[]{1}),
                "UNKNOWN", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported biz type");
    }
}