package com.platform.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_commands")
public class ReviewCommand {

    @TableId(value = "decision_id", type = IdType.INPUT)
    private String decisionId;
    private Long articleId;
    private String submissionId;
    private Long expectedVersion;
    private Long adminId;
    private ReviewAction action;
    private String reason;
    private String payloadHash;
    private String state;
    private ArticleStatus status;
    private Long articleVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String errorMessage;
}
