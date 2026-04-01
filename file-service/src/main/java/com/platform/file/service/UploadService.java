package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import org.springframework.web.multipart.MultipartFile;


public interface UploadService {

        UploadResp upload(MultipartFile file, String bizType, Long articleId);
}
