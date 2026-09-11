package com.platform.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.content.entity.ContentReviewDecision;
import com.platform.kernel.enums.ArticleStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ContentReviewDecisionMapper extends BaseMapper<ContentReviewDecision> {

    @Insert("""
            INSERT INTO content_review_decisions(
                decision_id, article_id, submission_id, expected_version,
                admin_id, action, reason, state, updated_at
            ) VALUES(
                #{decisionId}, #{articleId}, #{submissionId}, #{expectedVersion},
                #{adminId}, #{action}, #{reason}, 'PROCESSING', NOW(6)
            )
            ON DUPLICATE KEY UPDATE decision_id = decision_id
            """)
    int insertProcessing(ContentReviewDecision decision);

    @Select("SELECT * FROM content_review_decisions WHERE decision_id = #{decisionId} FOR UPDATE")
    ContentReviewDecision selectByDecisionIdForUpdate(@Param("decisionId") String decisionId);

    @Update("""
            UPDATE content_review_decisions
               SET state = #{state}, status = #{status}, article_version = #{articleVersion},
                   updated_at = #{updatedAt}
             WHERE decision_id = #{decisionId}
            """)
    int updateOutcome(@Param("decisionId") String decisionId,
                      @Param("state") String state,
                      @Param("status") ArticleStatus status,
                      @Param("articleVersion") Long articleVersion,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
