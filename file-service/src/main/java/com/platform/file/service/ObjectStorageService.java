package com.platform.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ObjectStorageService {

    String store(String objectKey, MultipartFile file) throws IOException;

    void validateReadiness();
}
