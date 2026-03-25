package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自动保存草稿响应
 * 前端用于展示"上次保存时间"和实时字数/阅读时长
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