package com.platform.service.impl;

import com.platform.dto.resp.UploadResp;
import com.platform.enums.BizType;
import com.platform.exception.BusinessException;
import com.platform.service.UploadService;
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

/**
 * 图片上传业务实现类，负责校验图片格式、写入本地磁盘并生成访问地址。
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    static {
        ImageIO.scanForPlugins();
    }

    /** 文件物理存储根目录。 */
    @Value("${storage.upload-path}")
    private String uploadBaseDir;

    /** 对外访问 URL 前缀。 */
    @Value("${storage.access-prefix}")
    private String staticUrlPrefix;

    /** 允许的文件扩展名。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 允许的 MIME 类型。 */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** 单文件大小上限，5MB。 */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /** 主色提取时的采样密度。 */
    private static final int SAMPLE_SIZE = 20;

    /**
     * 上传图片，校验格式后落盘，并在封面图场景下提取主色。
     */
    @Override
    public UploadResp upload(MultipartFile file, String bizType, Long articleId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }

        BizType biz;
        try {
            biz = BizType.valueOf(bizType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(
                    "不支持的业务类型：" + bizType + "，可选值为 AVATAR / COVER / ARTICLE_IMAGE"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest(
                    "文件大小不能超过 5MB，当前大小为 " + (file.getSize() / 1024) + "KB"
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.badRequest("仅支持 JPG / PNG / WebP 格式图片");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.badRequest("不支持的文件扩展名：" + ext);
        }

        BufferedImage image;
        try (InputStream is = file.getInputStream()) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            log.warn("图片解码失败，文件名={}", originalName, e);
            throw BusinessException.badRequest("图片解析失败，请确认文件内容是否有效");
        }
        if (image == null) {
            throw BusinessException.badRequest("上传文件不是有效的图片内容");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        String dominantColor = null;
        if (biz == BizType.COVER) {
            dominantColor = extractDominantColor(image);
        }

        LocalDate today = LocalDate.now();
        String relativePath = String.format("%d/%02d/%02d/%s.%s",
                today.getYear(), today.getMonthValue(), today.getDayOfMonth(),
                UUID.randomUUID(), ext);

        Path uploadRoot = Paths.get(uploadBaseDir).toAbsolutePath().normalize();
        Path physicalPath = uploadRoot.resolve(relativePath).normalize();
        if (!physicalPath.startsWith(uploadRoot)) {
            throw BusinessException.serverError("文件存储路径非法");
        }
        try {
            Files.createDirectories(physicalPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, physicalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("图片写入磁盘失败，路径={}", physicalPath, e);
            throw BusinessException.serverError("图片保存失败，请稍后重试");
        }

        String accessUrl = staticUrlPrefix + "/" + relativePath;

        log.info("文件上传成功: bizType={}, url={}, size={}", biz, accessUrl, file.getSize());

        return UploadResp.builder()
                .url(accessUrl)
                .width(width)
                .height(height)
                .size(file.getSize())
                .dominantColor(dominantColor)
                .build();
    }

    /**
     * 从文件名中提取小写扩展名。
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 通过中心区域采样估算图片主色，避免引入额外图像分析依赖。
     */
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
            log.warn("提取图片主色失败", e);
            return null;
        }
    }
}
