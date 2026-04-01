package com.platform.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("articles")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;
    private String title;
    private String content;
    private String summary;
    private String coverUrl;
    private String coverColor;
    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private Integer submitCount;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime publishedAt;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
