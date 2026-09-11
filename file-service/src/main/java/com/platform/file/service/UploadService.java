package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import org.springframework.web.multipart.MultipartFile;


public interface UploadService {

    /**
     * @param oldUrl 可选的兼容字段。客户端提供的 URL 不代表资源所有权，服务端不会据此删除对象。
     */
    UploadResp upload(MultipartFile file, String bizType, Long articleId, String oldUrl);
}
