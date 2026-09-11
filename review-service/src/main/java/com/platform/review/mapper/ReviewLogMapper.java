package com.platform.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.review.entity.ReviewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReviewLogMapper extends BaseMapper<ReviewLog> {

    @Select("SELECT * FROM review_logs WHERE decision_id = #{decisionId} LIMIT 1")
    ReviewLog selectByDecisionId(@Param("decisionId") String decisionId);
}
