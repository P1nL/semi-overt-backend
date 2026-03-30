package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.entity.Article;
import com.platform.enums.DurationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    Article selectHeroPrimary();

    List<Article> selectHeroSecondary();

    List<Article> selectRandomApproved(@Param("limit") int limit);

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
     * backed by Elasticsearch.</p>
     */
    IPage<Article> searchByKeyword(
            Page<Article> page,
            @Param("keyword") String keyword
    );
}
