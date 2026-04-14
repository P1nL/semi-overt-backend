package com.platform.file.service.impl;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.config.StorageConfig;
import com.platform.file.service.ObjectStorageService;
import com.platform.file.service.UploadService;
import com.platform.kernel.enums.BizType;
import com.platform.kernel.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final int SAMPLE_SIZE = 20;

    static {
        ImageIO.scanForPlugins();
    }

    private final ObjectStorageService objectStorageService;
    private final StorageConfig storageConfig;

    public UploadServiceImpl(ObjectStorageService objectStorageService, StorageConfig storageConfig) {
        this.objectStorageService = objectStorageService;
        this.storageConfig = storageConfig;
    }

    @Override
    public UploadResp upload(MultipartFile file, String bizType, Long articleId, String oldUrl) {
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

        long maxFileSize = storageConfig.getMaxFileSize() > 0 ? storageConfig.getMaxFileSize() : 5 * 1024 * 1024;
        if (file.getSize() > maxFileSize) {
            throw BusinessException.badRequest(
                    "File size must not exceed " + (maxFileSize / 1024 / 1024) + "MB, current size is " + (file.getSize() / 1024) + "KB");
        }

        String contentType = file.getContentType();
        List<String> allowedTypes = storageConfig.getAllowedTypes();
        if (contentType == null || allowedTypes == null || allowedTypes.stream().noneMatch(contentType::equalsIgnoreCase)) {
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
        String objectKey = String.format("%d/%02d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(), UUID.randomUUID(), ext);

        String accessUrl;
        try {
            accessUrl = objectStorageService.store(objectKey, file);
        } catch (IOException e) {
            log.error("Failed to persist image object: {}", objectKey, e);
            throw BusinessException.serverError("Failed to save uploaded image");
        }

        log.info("Upload succeeded: bizType={}, url={}, size={}", biz, accessUrl, file.getSize());

        // AVATAR / COVER 场景：新文件上传成功后删除旧文件（仅删除同一存储根下的文件）
        if ((biz == BizType.AVATAR || biz == BizType.COVER)
                && oldUrl != null && !oldUrl.isBlank()) {
            deleteOldFile(oldUrl);
        }

        return UploadResp.builder()
                .url(accessUrl)
                .width(width)
                .height(height)
                .size(file.getSize())
                .dominantColor(dominantColor)
                .build();
    }

    /**
     * 从旧文件 URL 中解析出 objectKey，并调用存储层删除。
     * 仅删除同一 accessPrefix 下的文件，防止误删外部 URL。
     */
    private void deleteOldFile(String oldUrl) {
        try {
            String prefix = storageConfig.getAccessPrefix();
            if (prefix == null || prefix.isBlank()) {
                return;
            }
            String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
            if (!oldUrl.startsWith(normalizedPrefix)) {
                // 不是本服务托管的文件，跳过
                return;
            }
            String objectKey = oldUrl.substring(normalizedPrefix.length());
            if (!objectKey.isBlank()) {
                objectStorageService.delete(objectKey);
            }
        } catch (Exception e) {
            log.warn("Failed to delete old file: {}, reason: {}", oldUrl, e.getMessage());
        }
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
