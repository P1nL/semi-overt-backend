package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceImplTest {

    private ObjectStorageService objectStorageService;
    private StorageConfig storageConfig;
    private UploadServiceImpl service;

    @BeforeEach
    void setUp() {
        objectStorageService = mock(ObjectStorageService.class);
        storageConfig = new StorageConfig();
        storageConfig.setAccessPrefix("/static/uploads");
        storageConfig.setAllowedTypes(List.of("image/jpeg", "image/png", "image/webp"));
        storageConfig.setMaxFileSize(5 * 1024 * 1024);
        service = new UploadServiceImpl(objectStorageService, storageConfig);
    }

    @Test
    void uploadShouldUseObjectStorageAndReturnStoredUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.webp",
                "image/webp",
                "fake-webp-content".getBytes(StandardCharsets.UTF_8)
        );
        BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
        when(objectStorageService.store(anyString(), any())).thenReturn("https://cdn.example.com/2026/04/01/object.webp");

        try (MockedStatic<ImageIO> imageIoMock = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            imageIoMock.when(() -> ImageIO.read(Mockito.any(InputStream.class))).thenReturn(image);

            UploadResp result = service.upload(file, "ARTICLE_IMAGE", 9L);

            assertThat(result.getWidth()).isEqualTo(12);
            assertThat(result.getHeight()).isEqualTo(8);
            assertThat(result.getUrl()).isEqualTo("https://cdn.example.com/2026/04/01/object.webp");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService).store(keyCaptor.capture(), any());
            assertThat(keyCaptor.getValue()).matches("\\d{4}/\\d{2}/\\d{2}/[a-f0-9\\-]+\\.webp");
        }
    }

    @Test
    void uploadShouldRejectUnsupportedMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.gif",
                "image/gif",
                "fake".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.upload(file, "ARTICLE_IMAGE", 9L))
                .hasMessageContaining("Only JPG / PNG / WebP images are supported");
    }
}
