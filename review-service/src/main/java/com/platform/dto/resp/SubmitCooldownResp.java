package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SubmitCooldownResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class SubmitCooldownResp {

    /**
     * Next allowed submit time.
     */
    private LocalDateTime nextSubmitAt;

    /**
     * Remaining cooldown seconds.
     */
    private Long remainingSeconds;
}
