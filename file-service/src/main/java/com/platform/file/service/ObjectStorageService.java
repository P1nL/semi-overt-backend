package com.platform.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ObjectStorageService {

    String store(String objectKey, MultipartFile file) throws IOException;

    /**
     * 删除指定对象。objectKey 不存在时静默忽略。
     */
    void delete(String objectKey);

    void validateReadiness();
}
