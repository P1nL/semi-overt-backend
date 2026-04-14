package com.platform.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.content.entity.Article;
import com.platform.kernel.enums.DurationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    Article selectHeroPrimary();

    List<Article> selectHeroSecondary();

    List<Article> selectRandomApproved(@Param("limit") int limit);

    /** 查询尚未上过首页的已审核文章（last_featured_at IS NULL），随机返回 limit 条 */
    List<Article> selectUnfeaturedApproved(@Param("limit") int limit);

    /** 查询已上过首页的文章，用于首页卡片不足时补充（last_featured_at IS NOT NULL） */
    List<Article> selectFeaturedApproved(@Param("limit") int limit);

    /** 批量标记文章为"已曝光"（更新 last_featured_at = NOW()） */
    void markAsFeatured(@Param("ids") List<Long> ids);

    /** 清空所有文章的 last_featured_at，开始新一轮轮替 */
    void resetAllFeatured();

    List<Article> selectApprovedByCategory(
            @Param("category") DurationCategory category,
            @Param("limit") int limit
    );

    IPage<Article> selectPageByCategory(
            Page<Article> page,
            @Param("category") DurationCategory category
    );

    /**
     * Historical database fuzzy-search query kept for single-service compatibility.
     *
     * <p>The active public search path now lives in {@code search-service} and is
     * backed by MySQL.</p>
     */
    IPage<Article> searchByKeyword(
            Page<Article> page,
            @Param("keyword") String keyword
    );
}



