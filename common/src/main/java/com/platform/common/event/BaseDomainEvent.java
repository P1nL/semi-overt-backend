package com.platform.common.event;

public interface BaseDomainEvent {

    String getEventId();

    String getTraceId();
}
