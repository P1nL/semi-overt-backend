package com.platform.review.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_logs")
public class ReviewLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private Long operatorId;
    private ReviewAction action;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
