package com.platform.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.review.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    @Select("SELECT * FROM review_tasks WHERE article_id = #{articleId} FOR UPDATE")
    ReviewTask selectByArticleIdForUpdate(@Param("articleId") Long articleId);

    @Select("SELECT * FROM review_tasks WHERE article_id = #{articleId}")
    ReviewTask selectByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT * FROM review_tasks WHERE id > #{afterId} ORDER BY id LIMIT #{limit}")
    List<ReviewTask> selectForReconciliationAfter(@Param("afterId") long afterId,
                                                   @Param("limit") int limit);
}
