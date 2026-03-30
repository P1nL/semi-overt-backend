package com.platform.common.event;

/**
 * 领域事件基类，提供事件类型、业务键和链路元数据等公共字段。
 */

public interface BaseDomainEvent {

    String getEventId();

    String getTraceId();
}
