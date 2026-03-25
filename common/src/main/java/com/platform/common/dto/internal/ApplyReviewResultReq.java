package com.platform.common.dto.internal;

import com.platform.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyReviewResultReq {
    private Long adminId;
    private ReviewAction action;
    private String reason;
}
