package com.platform.review.api.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提审冷却信息响应。
 */
@Data
@Builder
public class SubmitCooldownResp {

    /**
     * 下次允许提审的时间。
     */
    private LocalDateTime nextSubmitAt;

    /**
     * 距离允许再次提审还剩多少秒。
     */
    private Long remainingSeconds;
}


