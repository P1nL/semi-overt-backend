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



