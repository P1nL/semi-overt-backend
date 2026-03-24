package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核日志实体，对应 review_logs 表
 * 记录每次审核动作，包括 APPROVE / RETURN / REJECT / CANCEL
 */
@Data
@TableName("review_logs")
public class ReviewLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联文章 ID */
    private Long articleId;

    /** 操作人 ID（管理员或作者） */
    private Long operatorId;

    /** 审核动作 */
    private ReviewAction action;

    /** 操作前状态 */
    private ArticleStatus fromStatus;

    /** 操作后状态 */
    private ArticleStatus toStatus;

    /** 退回/拒绝原因（RETURN/REJECT 时必填，其余可为空） */
    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}