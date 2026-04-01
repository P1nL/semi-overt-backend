package com.platform.events.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.events.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件Outbox数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
