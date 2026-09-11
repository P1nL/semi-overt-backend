package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import com.platform.file.support.TestImageFixtures;
import com.platform.kernel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadSupportTest {

    @Test
    void acceptsImageIoGeneratedPngAndReturnsTrustedMetadata() throws Exception {
        StorageConfig config = config();
        ImageUploadSupport support = new ImageUploadSupport(config);
        byte[] bytes = TestImageFixtures.png(Color.decode("#336699"));

        ImageUploadSupport.ValidatedImage image = support.validate(
                new MockMultipartFile("file", "cover.png", "image/png", bytes));

        assertThat(image.bytes()).isEqualTo(bytes);
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(2);
        assertThat(image.height()).isEqualTo(2);
        assertThat(image.dominantColor()).isEqualTo("#336699");
        assertThat(image.objectKey()).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]+\\.png");
    }

    @Test
    void acceptsCompleteWebpAfterRealDecode() {
        ImageUploadSupport.ValidatedImage image = new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "cover.webp", "image/webp", TestImageFixtures.webp()));

        assertThat(image.width()).isEqualTo(2);
        assertThat(image.height()).isEqualTo(3);
        assertThat(image.contentType()).isEqualTo("image/webp");
        assertThat(image.dominantColor()).isEqualTo("#336699");
    }

    @Test
    void rejectsSvgDisguisedAsPng() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "avatar.png", "image/png", svg)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsEmptyFileBeforeDecoderOrProvider() {
        assertThatThrownBy(() -> new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Uploaded file is required");
    }

    @Test
    void rejectsExtensionAndDeclaredMimeThatDisagreeWithActualBytes() throws Exception {
        byte[] png = TestImageFixtures.png(Color.RED);

        assertThatThrownBy(() -> new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "wrong.jpg", "image/jpeg", png)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");

        assertThatThrownBy(() -> new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "wrong.png", "image/jpeg", png)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported image content type");
    }

    @Test
    void rejectsDeclaredDimensionBombBeforeFullDecode() throws Exception {
        byte[] png = TestImageFixtures.png(Color.BLUE);
        // PNG IHDR width/height positions; preserve the valid signature but
        // claim a 6000x5000 image, over the configured pixel limit.
        writeBigEndian(png, 16, 6000);
        writeBigEndian(png, 20, 5000);

        assertThatThrownBy(() -> new ImageUploadSupport(config()).validate(
                new MockMultipartFile("file", "large.png", "image/png", png)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dimensions");
    }

    private StorageConfig config() {
        StorageConfig config = new StorageConfig();
        config.setMaxFileSize(5 * 1024 * 1024L);
        config.setMaxImageDimension(8192);
        config.setMaxImagePixels(24_000_000L);
        config.setMaxConcurrentImageDecodes(2);
        return config;
    }

    private void writeBigEndian(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}