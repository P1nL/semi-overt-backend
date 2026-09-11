package com.platform.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ObjectStorageService {

    /**
     * Stores bytes that have already passed image validation. Providers must
     * not read an independent, unvalidated MultipartFile stream.
     */
    String store(String objectKey, byte[] bytes, String contentType) throws IOException;

    /** Compatibility overload for focused provider callers. */
    default String store(String objectKey, MultipartFile file) throws IOException {
        if (file == null) {
            throw new IOException("Uploaded file is required");
        }
        return store(objectKey, file.getBytes(), file.getContentType());
    }

    /** Delete an object only when a trusted internal caller supplies its key. */
    void delete(String objectKey);

    /** Validate provider configuration and local readiness without exposing secrets. */
    void validateReadiness();
}