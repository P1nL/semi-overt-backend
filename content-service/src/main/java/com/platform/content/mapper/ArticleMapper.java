package com.platform.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.content.entity.Article;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.WritingCalendarDayDto;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Insert("""
            INSERT INTO content_author_locks(author_id) VALUES(#{authorId})
            ON DUPLICATE KEY UPDATE author_id = VALUES(author_id)
            """)
    int ensureAuthorLock(@Param("authorId") Long authorId);

    @Select("SELECT author_id FROM content_author_locks WHERE author_id = #{authorId} FOR UPDATE")
    Long lockAuthor(@Param("authorId") Long authorId);

    /** Includes logically deleted rows so delete/review races can be diagnosed under one row lock. */
    @Select("SELECT * FROM articles WHERE id = #{articleId} FOR UPDATE")
    Article selectByIdForUpdate(@Param("articleId") Long articleId);

    @Select("SELECT * FROM articles WHERE id = #{articleId}")
    Article selectByIdIncludingDeleted(@Param("articleId") Long articleId);

    @Select("""
            SELECT * FROM articles
             WHERE id > #{afterId}
               AND status = 'PENDING'
               AND deleted = 0
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<Article> selectPendingReviewSnapshots(@Param("afterId") long afterId,
                                               @Param("limit") int limit);

    @Select("""
            SELECT
                COALESCE(SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved,
                COALESCE(SUM(CASE WHEN #{canViewAll} = TRUE AND status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending,
                COALESCE(SUM(CASE WHEN #{canViewAll} = TRUE AND status = 'RETURNED' THEN 1 ELSE 0 END), 0) AS returned,
                COALESCE(SUM(CASE WHEN #{canViewAll} = TRUE AND status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected,
                COALESCE(SUM(CASE WHEN #{canViewAll} = TRUE AND status = 'DRAFT' THEN 1 ELSE 0 END), 0) AS draft,
                COALESCE(SUM(CASE
                    WHEN #{canViewAll} = TRUE OR status = 'APPROVED' THEN COALESCE(word_count, 0)
                    ELSE 0
                END), 0) AS total_word_count
              FROM articles
             WHERE author_id = #{authorId}
               AND deleted = 0
            """)
    @Results({
            @Result(column = "approved", property = "approved"),
            @Result(column = "pending", property = "pending"),
            @Result(column = "returned", property = "returned"),
            @Result(column = "rejected", property = "rejected"),
            @Result(column = "draft", property = "draft"),
            @Result(column = "total_word_count", property = "totalWordCount")
    })
    UserProfileArticleStatsDto selectProfileStats(@Param("authorId") Long authorId,
                                                   @Param("canViewAll") boolean canViewAll);

    @Select("""
            SELECT COUNT(*) FROM articles
             WHERE author_id = #{authorId}
               AND deleted = 0
               AND status IN ('DRAFT','PENDING','RETURNED','REJECTED')
            """)
    long countDraftBoxByAuthor(@Param("authorId") Long authorId);

    @Update("""
            UPDATE articles
               SET title = #{title}, content = #{content}, summary = #{summary},
                   cover_url = #{coverUrl}, cover_color = #{coverColor},
                   word_count = #{wordCount}, read_minutes = #{readMinutes},
                   duration_category = #{durationCategory}, draft_visible = 0,
                   version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND author_id = #{authorId}
               AND status IN ('DRAFT','RETURNED')
               AND deleted = 0
               AND version = #{expectedVersion}
            """)
    int updateDraftFields(@Param("articleId") Long articleId,
                          @Param("authorId") Long authorId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("title") String title,
                          @Param("content") String content,
                          @Param("summary") String summary,
                          @Param("coverUrl") String coverUrl,
                          @Param("coverColor") String coverColor,
                          @Param("wordCount") Integer wordCount,
                          @Param("readMinutes") BigDecimal readMinutes,
                          @Param("durationCategory") DurationCategory durationCategory);

    @Update("""
            UPDATE articles
               SET status = 'PENDING', submission_id = #{submissionId},
                   submit_count = submit_count + 1, last_submitted_at = NOW(6),
                   draft_visible = 0, version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND author_id = #{authorId}
               AND status IN ('DRAFT','RETURNED')
               AND deleted = 0
               AND version = #{expectedVersion}
            """)
    int submitForReview(@Param("articleId") Long articleId,
                        @Param("authorId") Long authorId,
                        @Param("expectedVersion") Long expectedVersion,
                        @Param("submissionId") String submissionId);

    @Update("""
            UPDATE articles
               SET status = 'DRAFT', version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND author_id = #{authorId}
               AND status = 'PENDING'
               AND deleted = 0
               AND version = #{expectedVersion}
               AND submission_id = #{submissionId}
            """)
    int cancelReview(@Param("articleId") Long articleId,
                     @Param("authorId") Long authorId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("submissionId") String submissionId);

    @Update("""
            UPDATE articles
               SET deleted = 1, version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND author_id = #{authorId}
               AND status IN ('DRAFT','APPROVED','RETURNED','REJECTED')
               AND deleted = 0
               AND version = #{expectedVersion}
            """)
    int deleteByAuthorCas(@Param("articleId") Long articleId,
                          @Param("authorId") Long authorId,
                          @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE articles
               SET deleted = 1, version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND deleted = 0
               AND version = #{expectedVersion}
            """)
    int deleteByAdminCas(@Param("articleId") Long articleId,
                         @Param("expectedVersion") Long expectedVersion);

    @Update("""
            UPDATE articles
               SET status = #{toStatus},
                   published_at = CASE
                       WHEN #{toStatus} = 'APPROVED' AND published_at IS NULL THEN NOW(6)
                       ELSE published_at
                   END,
                   version = version + 1, updated_at = NOW(6)
             WHERE id = #{articleId}
               AND status = 'PENDING'
               AND deleted = 0
               AND submission_id = #{submissionId}
               AND version = #{expectedVersion}
            """)
    int applyReviewDecisionCas(@Param("articleId") Long articleId,
                               @Param("submissionId") String submissionId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("toStatus") ArticleStatus toStatus);

    Article selectHeroPrimary();

    List<Article> selectHeroSecondary();

    List<Article> selectRandomApproved(@Param("limit") int limit);

    List<Article> selectUnfeaturedApproved(@Param("limit") int limit);

    List<Article> selectFeaturedApproved(@Param("limit") int limit);

    void markAsFeatured(@Param("ids") List<Long> ids);

    void resetAllFeatured();

    List<Article> selectApprovedByCategory(@Param("category") DurationCategory category,
                                           @Param("limit") int limit);

    @Select("""
            SELECT a.*
              FROM articles a
              LEFT JOIN home_article_exposures e
                ON e.user_id = #{userId} AND e.article_id = a.id
             WHERE a.status = 'APPROVED'
               AND a.deleted = 0
               AND a.duration_category = #{category}
             ORDER BY CASE WHEN e.article_id IS NULL THEN 0 ELSE 1 END,
                      e.exposure_count ASC,
                      e.last_exposed_at ASC,
                      a.updated_at DESC,
                      a.id DESC
             LIMIT #{limit}
            """)
    List<Article> selectApprovedByCategoryForHomeUser(@Param("userId") Long userId,
                                                       @Param("category") DurationCategory category,
                                                       @Param("limit") int limit);

    @Insert("""
            INSERT INTO home_article_exposures(
                user_id, article_id, first_exposed_at, last_exposed_at, exposure_count
            ) VALUES (#{userId}, #{articleId}, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 1)
            ON DUPLICATE KEY UPDATE
                last_exposed_at = CURRENT_TIMESTAMP(6),
                exposure_count = exposure_count + 1
            """)
    int recordHomeExposure(@Param("userId") Long userId, @Param("articleId") Long articleId);

    @Select("""
            SELECT DATE(updated_at) AS activity_date,
                   COALESCE(SUM(word_count), 0) AS word_count
              FROM articles
             WHERE author_id = #{authorId}
               AND deleted = 0
               AND updated_at >= #{since}
             GROUP BY DATE(updated_at)
             ORDER BY activity_date ASC
            """)
    @Results({
            @Result(column = "activity_date", property = "date", javaType = LocalDate.class),
            @Result(column = "word_count", property = "wordCount")
    })
    List<WritingCalendarDayDto> selectWritingCalendar(@Param("authorId") Long authorId,
                                                       @Param("since") LocalDateTime since);

    @Select("""
            SELECT DATE(updated_at) AS activity_date,
                   COALESCE(SUM(word_count), 0) AS word_count
              FROM articles
             WHERE author_id = #{authorId}
               AND deleted = 0
               AND status = 'APPROVED'
               AND updated_at >= #{since}
             GROUP BY DATE(updated_at)
             ORDER BY activity_date ASC
            """)
    @Results({
            @Result(column = "activity_date", property = "date", javaType = LocalDate.class),
            @Result(column = "word_count", property = "wordCount")
    })
    List<WritingCalendarDayDto> selectApprovedWritingCalendar(@Param("authorId") Long authorId,
                                                               @Param("since") LocalDateTime since);

    IPage<Article> selectPageByCategory(Page<Article> page,
                                        @Param("category") DurationCategory category);

    IPage<Article> searchByKeyword(Page<Article> page,
                                   @Param("keyword") String keyword);
}