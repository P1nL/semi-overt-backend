package com.platform.contract.content.dto;

import com.platform.kernel.enums.ReviewAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Apply审核统一结果相关类型，承载当前模块中的辅助职责。
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyReviewResultReq {
    private Long adminId;
    private ReviewAction action;
    private String reason;
}
