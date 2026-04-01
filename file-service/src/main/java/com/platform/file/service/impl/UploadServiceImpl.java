package com.platform.file.service.impl;

import com.platform.file.api.resp.UploadResp;
import com.platform.kernel.enums.BizType;
import com.platform.kernel.exception.BusinessException;
import com.platform.file.service.UploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int SAMPLE_SIZE = 20;

    static {
        ImageIO.scanForPlugins();
    }

    @Value("${storage.upload-path}")
    private String uploadBaseDir;

    @Value("${storage.access-prefix}")
    private String staticUrlPrefix;

    @Override
    public UploadResp upload(MultipartFile file, String bizType, Long articleId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("Uploaded file is required");
        }

        BizType biz;
        try {
            biz = BizType.valueOf(bizType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(
                    "Unsupported biz type: " + bizType + ", expected AVATAR / COVER / ARTICLE_IMAGE");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest(
                    "File size must not exceed 5MB, current size is " + (file.getSize() / 1024) + "KB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.badRequest("Only JPG / PNG / WebP images are supported");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.badRequest("Unsupported file extension: " + ext);
        }

        BufferedImage image;
        try (InputStream is = file.getInputStream()) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            log.warn("Failed to decode image: {}", originalName, e);
            throw BusinessException.badRequest("Failed to parse image content");
        }
        if (image == null) {
            throw BusinessException.badRequest("Uploaded file is not a valid image");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        String dominantColor = biz == BizType.COVER ? extractDominantColor(image) : null;

        LocalDate today = LocalDate.now();
        String relativePath = String.format("%d/%02d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(), UUID.randomUUID(), ext);

        Path uploadRoot = Paths.get(uploadBaseDir).toAbsolutePath().normalize();
        Path physicalPath = uploadRoot.resolve(relativePath).normalize();
        if (!physicalPath.startsWith(uploadRoot)) {
            throw BusinessException.serverError("Invalid storage path");
        }

        try {
            Files.createDirectories(physicalPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, physicalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to write image to disk: {}", physicalPath, e);
            throw BusinessException.serverError("Failed to save uploaded image");
        }

        String accessUrl = staticUrlPrefix + "/" + relativePath;
        log.info("Upload succeeded: bizType={}, url={}, size={}", biz, accessUrl, file.getSize());

        return UploadResp.builder()
                .url(accessUrl)
                .width(width)
                .height(height)
                .size(file.getSize())
                .dominantColor(dominantColor)
                .build();
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String extractDominantColor(BufferedImage image) {
        try {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            int startX = imgWidth / 4;
            int startY = imgHeight / 4;
            int regionWidth = imgWidth / 2;
            int regionHeight = imgHeight / 2;
            int stepX = Math.max(1, regionWidth / SAMPLE_SIZE);
            int stepY = Math.max(1, regionHeight / SAMPLE_SIZE);

            long totalR = 0;
            long totalG = 0;
            long totalB = 0;
            int count = 0;

            for (int x = startX; x < startX + regionWidth; x += stepX) {
                for (int y = startY; y < startY + regionHeight; y += stepY) {
                    int rgb = image.getRGB(x, y);
                    totalR += (rgb >> 16) & 0xFF;
                    totalG += (rgb >> 8) & 0xFF;
                    totalB += rgb & 0xFF;
                    count++;
                }
            }

            if (count == 0) {
                return null;
            }

            int avgR = (int) (totalR / count);
            int avgG = (int) (totalG / count);
            int avgB = (int) (totalB / count);
            return String.format("#%02X%02X%02X", avgR, avgG, avgB);
        } catch (Exception e) {
            log.warn("Failed to extract dominant color", e);
            return null;
        }
    }
}
