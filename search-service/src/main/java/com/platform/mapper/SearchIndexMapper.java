package com.platform.mapper;

import com.platform.document.ArticleSearchDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchIndexMapper {

    @Select("""
            SELECT
                a.id AS article_id,
                a.author_id,
                a.title,
                a.summary,
                a.cover_url,
                a.cover_color,
                a.read_minutes,
                a.duration_category,
                a.published_at,
                a.updated_at
            FROM articles a
            WHERE a.status = 'APPROVED'
              AND a.deleted = 0
            ORDER BY a.id
            """)
    List<ArticleSearchDocument> selectApprovedArticlesForIndex();
}
