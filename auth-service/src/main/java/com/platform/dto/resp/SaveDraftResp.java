package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SaveDraftResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class SaveDraftResp {

    /** 本次保存时间 */
    private LocalDateTime savedAt;

    /** 后端计算的字数 */
    private Integer wordCount;

    /** 阅读时长（分钟），= wordCount / 300 */
    private BigDecimal readMinutes;

    /** 阅读时长分类 */
    private DurationCategory durationCategory;

    /** 当前文章状态（始终为 DRAFT 或 RETURNED） */
    private ArticleStatus status;
}