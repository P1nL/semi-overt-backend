package com.platform.content.api.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


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



