package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文章卡片响应
 * 复用场景：首页、分类详情页、搜索结果页、个人主页文章列表
 */
@Data
@Builder
public class ArticleCardResp {

    private Long articleId;
    private String title;
    private String summary;
    private String previewText;
    private String coverUrl;

    /**
     * 封面主色，用于搜索/分类页卡片背景氛围
     * 上传封面图时提取，可为 null（前端使用默认色）
     */
    private String coverColor;

    private BigDecimal readMinutes;
    private DurationCategory durationCategory;

    /** 文章当前状态，个人主页列表需要展示 */
    private ArticleStatus status;

    private Long authorId;
    private String authorName;
    private String authorAvatar;

    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;

    /**
     * 最新一次退回/拒绝原因
     * 仅个人主页「拒绝」Tab 场景需要，其余场景返回 null。
     * Service 层按需填充，首页/分类/搜索不查询此字段。
     */
    private String rejectReason;
}
