package com.platform.common.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch用户Query相关类型，承载当前模块中的辅助职责。
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUserQueryReq {
    private List<Long> userIds;
}
