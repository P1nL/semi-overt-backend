package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.ReviewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审核日志数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface ReviewLogMapper extends BaseMapper<ReviewLog> {
}