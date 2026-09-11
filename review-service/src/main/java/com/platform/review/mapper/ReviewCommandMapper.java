package com.platform.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.review.entity.ReviewCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewCommandMapper extends BaseMapper<ReviewCommand> {

    @Select("SELECT * FROM review_commands WHERE decision_id = #{decisionId} FOR UPDATE")
    ReviewCommand selectForUpdate(@Param("decisionId") String decisionId);

    @Select("SELECT * FROM review_commands WHERE article_id = #{articleId} AND submission_id = #{submissionId} FOR UPDATE")
    ReviewCommand selectSubmissionForUpdate(@Param("articleId") Long articleId,
                                             @Param("submissionId") String submissionId);

    @Select("SELECT * FROM review_commands WHERE state = 'PROCESSING' AND decision_id > #{afterDecisionId} "
            + "ORDER BY decision_id LIMIT #{limit}")
    List<ReviewCommand> selectProcessingAfter(@Param("afterDecisionId") String afterDecisionId,
                                              @Param("limit") int limit);
}
