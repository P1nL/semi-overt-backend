package com.platform.enums;

/**
 * 文章状态枚举
 * 状态流转：DRAFT → PENDING → APPROVED / RETURNED / REJECTED
 *          RETURNED → PENDING（重新提交）
 *          PENDING → DRAFT（取消审核）
 */
public enum ArticleStatus {
    /** 草稿，作者可编辑 */
    DRAFT,
    /** 已提交待审，正文锁定 */
    PENDING,
    /** 审核通过，对外可见 */
    APPROVED,
    /** 退回修改，作者可继续编辑 */
    RETURNED,
    /** 已拒绝（终态），只能查看或删除 */
    REJECTED
}