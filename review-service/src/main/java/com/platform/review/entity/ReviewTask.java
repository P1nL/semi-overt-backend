package com.platform.review.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.kernel.enums.ArticleStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_tasks")
public class ReviewTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long authorId;
    private String title;
    private Integer wordCount;
    private ArticleStatus status;
    private Integer submitCount;
    private LocalDateTime submittedAt;
    private String lastEventId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assignedAdminId;
    private String submissionId;
    private Long lastAppliedVersion;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String decisionId;
    private String commandState;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

