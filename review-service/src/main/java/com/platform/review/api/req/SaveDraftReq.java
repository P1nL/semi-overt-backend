package com.platform.review.api.req;

import lombok.Data;

/**
 * 保存草稿请求。
 */
@Data
public class SaveDraftReq {

    /** 标题。 */
    private String title;

    /** Markdown 正文。 */
    private String content;

    /** 摘要，建议控制在 120 字以内。 */
    private String summary;

    /** 封面图 URL。 */
    private String coverUrl;

    /** 封面主色，通常用于无图或占位展示。 */
    private String coverColor;

    /** 客户端统计的字数，服务端可用于兜底或校验。 */
    private Integer clientWordCount;
}
