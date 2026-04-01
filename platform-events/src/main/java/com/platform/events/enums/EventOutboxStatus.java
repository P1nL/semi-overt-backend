package com.platform.events.enums;

/**
 * 事件Outbox状态枚举，表示相关领域中的有限状态或类型。
 */

public enum EventOutboxStatus {
    PENDING,
    PUBLISHED,
    DEAD
}
