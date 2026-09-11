package com.platform.file.service.impl;

import com.platform.file.config.StorageConfig;
import org.springframework.beans.factory.annotation.Autowired;
import com.platform.kernel.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Validates image bytes before any storage provider is called. Filename and
 * multipart MIME are consistency checks only; ImageIO metadata and a complete
 * decode are the source of truth for the accepted image.
 */
@Slf4j
@Component
public class ImageUploadSupport {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_DOMINANT_COLOR_SAMPLES = 4096;

    private final long maxFileSize;
    private final int maxDimension;
    private final long maxPixels;
    private final Semaphore decodeSlots;

    static {
        ImageIO.scanForPlugins();
    }

    @Autowired
    public ImageUploadSupport(StorageConfig storageConfig) {
        this(storageConfig.getMaxFileSize() > 0 ? storageConfig.getMaxFileSize() : 5 * 1024 * 1024L,
                storageConfig.getMaxImageDimension(), storageConfig.getMaxImagePixels(),
                storageConfig.getMaxConcurrentImageDecodes());
    }

    public ImageUploadSupport(long maxFileSize) {
        this(maxFileSize, 8192, 24_000_000L, 2);
    }

    public ImageUploadSupport(long maxFileSize, int maxDimension, long maxPixels, int maxConcurrentDecodes) {
        this.maxFileSize = Math.max(1, maxFileSize);
        this.maxDimension = Math.max(1, maxDimension);
        this.maxPixels = Math.max(1, maxPixels);
        this.decodeSlots = new Semaphore(Math.max(1, maxConcurrentDecodes), true);
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Uploaded file is required");
        }
        if (file.getSize() > maxFileSize) {
            throw BusinessException.badRequest("Uploaded file exceeds the configured byte limit");
        }

        String extension = extensionOf(file.getOriginalFilename());
        String declaredContentType = baseContentType(file.getContentType());
        if (declaredContentType != null && !ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            throw BusinessException.badRequest("Unsupported image content type: " + file.getContentType());
        }
        if (!decodeSlots.tryAcquire()) {
            throw BusinessException.tooManyRequests("Too many image decodes are in progress");
        }
        try {
            byte[] bytes = readBounded(file);
            ImageFormat expected = ImageFormat.forExtension(extension);
            ImageFormat actual = detectFormat(bytes);
            if (actual == null || actual != expected) {
                throw BusinessException.badRequest("Uploaded image content does not match its extension");
            }
            if (declaredContentType != null && !actual.contentType.equals(declaredContentType)) {
                throw BusinessException.badRequest("Unsupported image content type: " + file.getContentType());
            }
            if (actual == ImageFormat.WEBP) {
                requireWebpFrame(bytes);
            }

            ImageMetadata metadata = inspectMetadata(bytes, actual);
            enforceDimensions(metadata.width(), metadata.height());
            String dominantColor = decodeAndSample(bytes, metadata);
            return new ValidatedImage(objectKey(actual.extensionFor(extension)), bytes,
                    actual.contentType, metadata.width(), metadata.height(), dominantColor);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.debug("Rejected image upload after decoder inspection: {}", ex.getMessage());
            throw BusinessException.badRequest("Uploaded file is not a valid image");
        } finally {
            decodeSlots.release();
        }
    }

    private byte[] readBounded(MultipartFile file) throws IOException {
        int initialCapacity = (int) Math.min(Integer.MAX_VALUE,
                Math.max(32, Math.min(maxFileSize, Math.max(0, file.getSize()))));
        try (InputStream input = file.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxFileSize) {
                    throw BusinessException.badRequest("Uploaded file exceeds the configured byte limit");
                }
                output.write(buffer, 0, read);
            }
            if (total == 0) {
                throw BusinessException.badRequest("Uploaded file is required");
            }
            return output.toByteArray();
        }
    }

    private void requireWebpFrame(byte[] bytes) throws IOException {
        if (bytes.length < 20 || littleEndian32(bytes, 4) != bytes.length - 8L
                || !hasWebpFrame(bytes, 12, bytes.length, true)) {
            throw new IOException("WebP frame is missing or truncated");
        }
    }

    private boolean hasWebpFrame(byte[] bytes, int start, int end, boolean allowAnimation) throws IOException {
        boolean found = false;
        int offset = start;
        while (offset < end) {
            if (end - offset < 8) {
                throw new IOException("Truncated WebP chunk header");
            }
            long length = littleEndian32(bytes, offset + 4);
            long next = (long) offset + 8 + length + (length & 1);
            if (next > end) {
                throw new IOException("Truncated WebP chunk");
            }
            if (matchesAscii(bytes, offset, "VP8 ")) {
                if (length < 10) throw new IOException("Truncated VP8 frame");
                found = true;
            } else if (matchesAscii(bytes, offset, "VP8L")) {
                if (length < 5) throw new IOException("Truncated VP8L frame");
                found = true;
            } else if (allowAnimation && matchesAscii(bytes, offset, "ANMF")) {
                if (length < 24 || !hasWebpFrame(bytes, offset + 24,
                        (int) (offset + 8 + length), false)) {
                    throw new IOException("Animated WebP frame is missing");
                }
                found = true;
            }
            offset = (int) next;
        }
        return found;
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (matches(bytes, 0, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A})) return ImageFormat.PNG;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return ImageFormat.JPEG;
        if (matchesAscii(bytes, 0, "RIFF") && matchesAscii(bytes, 8, "WEBP")) return ImageFormat.WEBP;
        return null;
    }

    private ImageMetadata inspectMetadata(byte[] bytes, ImageFormat format) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("No ImageIO input stream");
            ImageReader reader = findReader(input, format);
            if (reader == null) throw new IOException("No matching ImageIO reader");
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) throw new IOException("Invalid image dimensions");
                return new ImageMetadata(format, width, height);
            } finally {
                reader.dispose();
            }
        }
    }

    private String decodeAndSample(byte[] bytes, ImageMetadata metadata) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("No ImageIO input stream");
            ImageReader reader = findReader(input, metadata.format());
            if (reader == null) throw new IOException("No matching ImageIO reader");
            try {
                reader.setInput(input, true, true);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != metadata.width()
                        || image.getHeight() != metadata.height()) {
                    throw new IOException("Decoded dimensions do not match metadata");
                }
                return dominantColor(image);
            } finally {
                reader.dispose();
            }
        }
    }

    private ImageReader findReader(ImageInputStream input, ImageFormat expected) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            if (expected.readerFormat.equalsIgnoreCase(reader.getFormatName())) return reader;
            reader.dispose();
        }
        return null;
    }

    private void enforceDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                || ((long) width * height) > maxPixels) {
            throw BusinessException.badRequest("Image dimensions exceed the configured limit");
        }
    }

    private String dominantColor(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long pixels = (long) width * height;
        int columns = pixels <= MAX_DOMINANT_COLOR_SAMPLES ? width : Math.min(width, 64);
        int rows = pixels <= MAX_DOMINANT_COLOR_SAMPLES ? height : Math.min(height, 64);
        long red = 0, green = 0, blue = 0, count = 0;
        for (int column = 0; column < columns; column++) {
            int x = pixels <= MAX_DOMINANT_COLOR_SAMPLES ? column
                    : Math.min(width - 1, (int) (((long) (column * 2 + 1) * width) / (columns * 2L)));
            for (int row = 0; row < rows; row++) {
                int y = pixels <= MAX_DOMINANT_COLOR_SAMPLES ? row
                        : Math.min(height - 1, (int) (((long) (row * 2 + 1) * height) / (rows * 2L)));
                int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) == 0) continue;
                red += (argb >>> 16) & 0xFF;
                green += (argb >>> 8) & 0xFF;
                blue += argb & 0xFF;
                count++;
            }
        }
        return count == 0 ? null : "#%02x%02x%02x".formatted(red / count, green / count, blue / count);
    }

    private String extensionOf(String filename) {
        String cleanName = StringUtils.cleanPath(filename == null ? "" : filename);
        int index = cleanName.lastIndexOf('.');
        if (index < 0 || index == cleanName.length() - 1) {
            throw BusinessException.badRequest("Image filename extension is required");
        }
        String extension = cleanName.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest("Unsupported image extension: " + extension);
        }
        return extension;
    }

    private String baseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return null;
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String objectKey(String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "%d/%02d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(),
                today.getDayOfMonth(), UUID.randomUUID(), extension);
    }

    private boolean matches(byte[] bytes, int offset, byte[] expected) {
        if (offset < 0 || bytes.length < offset + expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (bytes[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private boolean matchesAscii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private long littleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 255L) | ((bytes[offset + 1] & 255L) << 8)
                | ((bytes[offset + 2] & 255L) << 16) | ((bytes[offset + 3] & 255L) << 24);
    }

    public record ValidatedImage(String objectKey, byte[] bytes, String contentType,
                                 int width, int height, String dominantColor) {
    }

    private record ImageMetadata(ImageFormat format, int width, int height) {
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpeg", "JPEG"),
        PNG("image/png", "png", "PNG"),
        WEBP("image/webp", "webp", "WEBP");

        private final String contentType;
        private final String defaultExtension;
        private final String readerFormat;

        ImageFormat(String contentType, String defaultExtension, String readerFormat) {
            this.contentType = contentType;
            this.defaultExtension = defaultExtension;
            this.readerFormat = readerFormat;
        }

        static ImageFormat forExtension(String extension) {
            return switch (extension) {
                case "jpg", "jpeg" -> JPEG;
                case "png" -> PNG;
                case "webp" -> WEBP;
                default -> throw BusinessException.badRequest("Unsupported image extension: " + extension);
            };
        }

        String extensionFor(String originalExtension) {
            return this == JPEG && "jpg".equals(originalExtension) ? "jpg" : defaultExtension;
        }
    }
}