package com.platform.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.common.entity.EventConsumeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事件消费日志数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface EventConsumeLogMapper extends BaseMapper<EventConsumeLog> {
}
