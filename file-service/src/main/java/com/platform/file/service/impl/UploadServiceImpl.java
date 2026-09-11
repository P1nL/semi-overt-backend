package com.platform.file.service.impl;

import com.platform.file.api.resp.UploadResp;
import com.platform.file.service.ObjectStorageService;
import com.platform.file.service.UploadService;
import com.platform.kernel.enums.BizType;
import com.platform.kernel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    private final ObjectStorageService objectStorageService;
    private final ImageUploadSupport imageUploadSupport;

    @Autowired
    public UploadServiceImpl(ObjectStorageService objectStorageService, ImageUploadSupport imageUploadSupport) {
        this.objectStorageService = objectStorageService;
        this.imageUploadSupport = imageUploadSupport;
    }

    /** Compatibility constructor for focused tests/callers. */
    public UploadServiceImpl(ObjectStorageService objectStorageService,
                             com.platform.file.config.StorageConfig storageConfig) {
        this(objectStorageService, new ImageUploadSupport(storageConfig));
    }

    @Override
    public UploadResp upload(MultipartFile file, String bizType, Long articleId, String oldUrl) {
        BizType biz;
        try {
            biz = BizType.valueOf(bizType == null ? "" : bizType.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest(
                    "Unsupported biz type: " + bizType + ", expected AVATAR / COVER / ARTICLE_IMAGE");
        }

        ImageUploadSupport.ValidatedImage image = imageUploadSupport.validate(file);
        final String accessUrl;
        try {
            accessUrl = objectStorageService.store(image.objectKey(), image.bytes(), image.contentType());
        } catch (IOException ex) {
            log.error("Failed to persist image object: {}", image.objectKey(), ex);
            throw BusinessException.serverError("Failed to save uploaded image");
        }

        // oldUrl is accepted for frontend wire compatibility. A client-supplied
        // URL does not establish ownership, so replacement cleanup is disabled.
        log.info("Upload succeeded: bizType={}, objectKey={}, size={}",
                biz, image.objectKey(), image.bytes().length);
        return UploadResp.builder()
                .url(accessUrl)
                .width(image.width())
                .height(image.height())
                .size((long) image.bytes().length)
                .dominantColor(image.dominantColor())
                .build();
    }
}