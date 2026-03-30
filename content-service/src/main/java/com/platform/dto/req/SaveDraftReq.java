package com.platform.dto.req;

import lombok.Data;

/**
 * SaveDraftReq 请求模型，承载对应场景的入参字段。
 */

@Data
public class SaveDraftReq {

    /** 文章标题，草稿阶段可为空 */
    private String title;

    /** Markdown 正文，写入 Redis 缓存 */
    private String content;

    /** 摘要，若为空后端自动截取正文前 120 字 */
    private String summary;

    /** 封面图访问 URL */
    private String coverUrl;

    /** 封面主色（前端上传图片时提取，可选） */
    private String coverColor;

    /**
     * 前端计算的字数，仅用于调试对比
     * 后端以自身计算为准，不使用此字段落库
     */
    private Integer clientWordCount;
}