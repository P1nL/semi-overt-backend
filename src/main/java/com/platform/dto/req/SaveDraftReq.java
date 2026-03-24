package com.platform.dto.req;

import lombok.Data;

/**
 * 自动保存草稿请求
 * 所有字段均可为 null（前端只发变化的字段也可，后端做 null 判断）
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