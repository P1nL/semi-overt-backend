package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.impl.ImageUploadSupport;
import com.platform.file.service.impl.UploadServiceImpl;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceImplTest {
    private ObjectStorageService objectStorageService;
    private UploadServiceImpl service;

    @BeforeEach
    void setUp() {
        objectStorageService = mock(ObjectStorageService.class);
        StorageConfig storageConfig = new StorageConfig();
        storageConfig.setMaxFileSize(5 * 1024 * 1024L);
        service = new UploadServiceImpl(objectStorageService, new ImageUploadSupport(storageConfig));
    }

    @Test
    void validatesRealImageBeforeProviderAndReturnsTrustedMetadata() throws Exception {
        byte[] png = TestImageFixtures.png(Color.decode("#336699"));
        when(objectStorageService.store(anyString(), any(byte[].class), anyString()))
                .thenReturn("/static/uploads/2026/09/11/object.png");

        UploadResp result = service.upload(
                new MockMultipartFile("file", "cover.png", "image/png", png),
                "COVER", 9L, "/static/uploads/other-user.png");

        assertThat(result.getUrl()).isEqualTo("/static/uploads/2026/09/11/object.png");
        assertThat(result.getWidth()).isEqualTo(2);
        assertThat(result.getHeight()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo((long) png.length);
        assertThat(result.getDominantColor()).isEqualTo("#336699");
        verify(objectStorageService).store(anyString(), any(byte[].class), anyString());
        verify(objectStorageService, never()).delete(anyString());
    }

    @Test
    void rejectsUnsupportedBusinessTypeBeforeProvider() throws Exception {
        byte[] png = TestImageFixtures.png(Color.BLUE);

        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "cover.png", "image/png", png),
                "UNKNOWN", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported biz type");
        verify(objectStorageService, never()).store(anyString(), any(byte[].class), anyString());
    }
}
