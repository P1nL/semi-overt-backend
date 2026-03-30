package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文章实体，映射持久化层中的相关数据记录。
 */

@Data
@TableName("articles")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者 ID */
    private Long authorId;

    /** 标题，草稿阶段可为空（前端显示"未命名草稿"） */
    private String title;

    /**
     * Markdown 正文
     * 草稿状态下正文主要在 Redis 中缓存，定时任务刷盘到此字段
     * Key: draft:{userId}:{articleId}
     */
    private String content;

    /** 摘要，空则后端截取正文前120字 */
    private String summary;

    /** 封面图访问 URL */
    private String coverUrl;

    /** 封面主色（可选，搜索/分类页氛围色用） */
    private String coverColor;

    /** 字数（后端同步更新） */
    private Integer wordCount;

    /** 阅读时长（分钟），= wordCount / 300 */
    private BigDecimal readMinutes;

    /** 阅读时长分类：QUICK / SHORT / DEEP */
    private DurationCategory durationCategory;

    /** 文章状态 */
    private ArticleStatus status;

    /** 累计提交审核次数 */
    private Integer submitCount;

    /** 最近一次提交审核时间（用于30分钟限流校验） */
    private LocalDateTime lastSubmittedAt;

    /** 审核通过（发布）时间 */
    private LocalDateTime publishedAt;

    /** 逻辑删除标记（0=正常，1=已删除） */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}