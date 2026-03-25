package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Submit review cooldown info.
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
