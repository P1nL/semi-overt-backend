package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.service.impl.UploadServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;


class UploadServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadShouldAcceptWebpImages() throws Exception {
        UploadServiceImpl service = new UploadServiceImpl();
        ReflectionTestUtils.setField(service, "uploadBaseDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "staticUrlPrefix", "/static/uploads");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.webp",
                "image/webp",
                "fake-webp-content".getBytes(StandardCharsets.UTF_8)
        );

        BufferedImage image = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);

        try (MockedStatic<ImageIO> imageIoMock = Mockito.mockStatic(ImageIO.class, Mockito.CALLS_REAL_METHODS)) {
            imageIoMock.when(() -> ImageIO.read(Mockito.any(InputStream.class))).thenReturn(image);

            UploadResp result = service.upload(file, "ARTICLE_IMAGE", 9L);

            assertThat(result.getWidth()).isEqualTo(12);
            assertThat(result.getHeight()).isEqualTo(8);
            assertThat(result.getUrl()).contains("/static/uploads/");
            assertThat(result.getUrl()).endsWith(".webp");
            assertThat(Files.walk(tempDir).count()).isGreaterThan(1);
        }
    }
}

