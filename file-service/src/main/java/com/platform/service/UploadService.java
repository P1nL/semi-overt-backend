package com.platform.service;

import com.platform.dto.resp.UploadResp;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传业务接口，定义对外暴露的服务能力。
 */

public interface UploadService {

    /**
     * 上传图片
     * 服务端校验：
     *   - 文件非空
     *   - 格式仅允许 jpg / png / webp（校验扩展名 + MIME）
     *   - 文件大小不超过 5MB
     * 存储路径：/data/app/uploads/{year}/{month}/{day}/{uuid}.ext
     * 对外访问：/static/uploads/{year}/{month}/{day}/{uuid}.ext
     *
     * @param file      上传文件
     * @param bizType   业务类型字符串：AVATAR / COVER / ARTICLE_IMAGE
     * @param articleId 文章 ID，正文图片场景可传，其他场景传 null
     * @return 上传结果（URL + 图片尺寸 + 主色）
     */
    UploadResp upload(MultipartFile file, String bizType, Long articleId);
}