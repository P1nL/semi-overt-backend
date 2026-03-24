package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * 图片上传响应
 * 对应接口：POST /api/v1/uploads/images
 */
@Data
@Builder
public class UploadResp {

    /** 文件访问 URL（相对路径，如 /static/uploads/2026/03/12/uuid.webp） */
    private String url;

    /** 图片宽度（像素） */
    private Integer width;

    /** 图片高度（像素） */
    private Integer height;

    /** 文件大小（字节） */
    private Long size;

    /**
     * 封面主色（HEX 格式，如 #AAB7C3）
     * 仅 bizType=COVER 时尝试提取；提取失败或其他场景返回 null。
     * 前端用于搜索/分类页横滑卡片背景氛围色。
     */
    private String dominantColor;
}