package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.NotificationDelivery;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知投递数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface NotificationDeliveryMapper extends BaseMapper<NotificationDelivery> {
}
