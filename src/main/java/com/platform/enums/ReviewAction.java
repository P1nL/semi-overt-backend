package com.platform.enums;

/** 审核操作类型 */
public enum ReviewAction {
    /** 通过 */
    APPROVE,
    /** 退回修改 */
    RETURN,
    /** 拒绝（终态） */
    REJECT,
    /** 作者取消审核 */
    CANCEL
}