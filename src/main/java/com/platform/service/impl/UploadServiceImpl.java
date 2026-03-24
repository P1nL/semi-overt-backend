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
 * 图片上传服务实现
 *
 * 存储策略：
 *   物理路径：{uploadBaseDir}/{year}/{month}/{day}/{uuid}.ext
 *   访问 URL：{staticUrlPrefix}/{year}/{month}/{day}/{uuid}.ext
 *   数据库保存相对 URL，避免部署路径变更时批量改库。
 *
 * 主色提取：
 *   对图片进行降采样（取中心区域 20x20 像素），
 *   计算 RGB 均值并转为 HEX，足够满足首页/分类页氛围色需求。
 *   提取失败时返回 null，前端使用默认颜色。
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    static {
        ImageIO.scanForPlugins();
    }

    /** 文件物理存储根目录，配置在 application.yml */
    @Value("${storage.upload-path}")
    private String uploadBaseDir;

    /** 对外访问 URL 前缀，配置在 application.yml */
    @Value("${storage.access-prefix}")
    private String staticUrlPrefix;

    /** 允许的文件扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 允许的 MIME 类型 */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** 最大文件大小：5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /** 主色提取的降采样尺寸（取图片中心区域，采样格子越小越快） */
    private static final int SAMPLE_SIZE = 20;

    @Override
    public UploadResp upload(MultipartFile file, String bizType, Long articleId) {
        // ---- 1. 基础校验 ----
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件不能为空");
        }

        // 解析 bizType 枚举（大小写不敏感）
        BizType biz;
        try {
            biz = BizType.valueOf(bizType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无效的业务类型：" + bizType
                    + "，支持：AVATAR / COVER / ARTICLE_IMAGE");
        }

        // ---- 2. 校验文件大小 ----
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("文件超过 5MB 限制，当前大小：" + (file.getSize() / 1024) + "KB");
        }

        // ---- 3. 校验 MIME 类型（后端二次校验，不信任前端传值） ----
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw BusinessException.badRequest("不支持的文件类型，仅允许 JPG / PNG / WebP");
        }

        // ---- 4. 校验扩展名（与 MIME 双重验证） ----
        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.badRequest("不支持的文件扩展名：." + ext);
        }

        // ---- 5. 读取图片、获取尺寸、提取主色 ----
        BufferedImage image;
        try (InputStream is = file.getInputStream()) {
            image = ImageIO.read(is);
        } catch (IOException e) {
            log.warn("读取图片失败，文件名：{}", originalName, e);
            throw BusinessException.badRequest("图片文件损坏或格式不正确");
        }
        if (image == null) {
            throw BusinessException.badRequest("无法解析图片内容，请确认文件格式");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 仅封面图场景提取主色（COVER），其他场景不需要
        String dominantColor = null;
        if (biz == BizType.COVER) {
            dominantColor = extractDominantColor(image);
        }

        // ---- 6. 生成存储路径并写入磁盘 ----
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
            log.error("文件写入磁盘失败，路径：{}", physicalPath, e);
            throw BusinessException.serverError("文件保存失败，请稍后重试");
        }

        // ---- 7. 拼接对外访问 URL（数据库存相对路径） ----
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

    // ==================== 私有辅助方法 ====================

    /**
     * 从文件名中提取扩展名（小写）
     * 若文件名为空或无扩展名，返回空字符串
     */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 提取图片主色（降采样均值法）
     *
     * 思路：
     *   1. 在图片中心区域取 SAMPLE_SIZE x SAMPLE_SIZE 个采样点
     *   2. 计算所有采样像素的 RGB 平均值
     *   3. 转换为 HEX 格式
     *
     * 这是一个"够用"的轻量方案，不需要引入额外依赖。
     * 如需更精确的主色提取（如中位切割算法），可引入 color-thief 等库替换。
     *
     * @param image 已加载的 BufferedImage
     * @return HEX 颜色字符串（如 #AAB7C3），失败返回 null
     */
    private String extractDominantColor(BufferedImage image) {
        try {
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();

            // 取中心区域（避免边框、黑边影响主色计算）
            int startX = imgWidth / 4;
            int startY = imgHeight / 4;
            int regionWidth = imgWidth / 2;
            int regionHeight = imgHeight / 2;

            // 计算采样步长
            int stepX = Math.max(1, regionWidth / SAMPLE_SIZE);
            int stepY = Math.max(1, regionHeight / SAMPLE_SIZE);

            long totalR = 0, totalG = 0, totalB = 0;
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

            if (count == 0) return null;

            int avgR = (int) (totalR / count);
            int avgG = (int) (totalG / count);
            int avgB = (int) (totalB / count);

            return String.format("#%02X%02X%02X", avgR, avgG, avgB);
        } catch (Exception e) {
            log.warn("主色提取失败", e);
            return null;
        }
    }
}
