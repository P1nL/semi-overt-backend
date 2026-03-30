package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SubmitResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class SubmitResp {

    /** 提交后状态，固定为 PENDING */
    private ArticleStatus status;

    /** 累计提交审核次数 */
    private Integer submitCount;

    /** 本次提交时间（用于前端展示限流倒计时） */
    private LocalDateTime lastSubmittedAt;
}