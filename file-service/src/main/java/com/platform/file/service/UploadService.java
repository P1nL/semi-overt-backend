package com.platform.file.service;

import com.platform.file.api.resp.UploadResp;
import org.springframework.web.multipart.MultipartFile;


public interface UploadService {

    /**
     * @param oldUrl 可选。AVATAR/COVER 场景下传入旧文件访问 URL，上传成功后自动删除旧文件。
     */
    UploadResp upload(MultipartFile file, String bizType, Long articleId, String oldUrl);
}
