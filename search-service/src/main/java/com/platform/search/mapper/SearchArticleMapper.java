package com.platform.search.mapper;

import com.platform.search.model.SearchArticleRow;
import com.platform.search.model.SearchKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface SearchArticleMapper {

    @SelectProvider(type = SearchArticleSqlProvider.class, method = "count")
    long countByKeyword(@Param("query") SearchKeyword query,
                        @Param("fullText") boolean fullText);

    @SelectProvider(type = SearchArticleSqlProvider.class, method = "search")
    @Results(id = "searchArticleRow", value = {
            @Result(column = "article_id", property = "articleId"),
            @Result(column = "author_id", property = "authorId"),
            @Result(column = "title", property = "title"),
            @Result(column = "summary", property = "summary"),
            @Result(column = "content", property = "content"),
            @Result(column = "cover_url", property = "coverUrl"),
            @Result(column = "cover_color", property = "coverColor"),
            @Result(column = "word_count", property = "wordCount"),
            @Result(column = "read_minutes", property = "readMinutes"),
            @Result(column = "duration_category", property = "durationCategory"),
            @Result(column = "status", property = "status"),
            @Result(column = "published_at", property = "publishedAt"),
            @Result(column = "updated_at", property = "updatedAt")
    })
    List<SearchArticleRow> searchByKeyword(@Param("query") SearchKeyword query,
                                           @Param("fullText") boolean fullText,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    /** Checks the actual current database, including exact type, columns and order. */
    @Select("""
            SELECT CASE
                     WHEN COUNT(*) = 3
                      AND SUM(CASE WHEN s.index_type = 'FULLTEXT' THEN 1 ELSE 0 END) = 3
                      AND SUM(CASE WHEN s.column_name = 'title' AND s.seq_in_index = 1 THEN 1 ELSE 0 END) = 1
                      AND SUM(CASE WHEN s.column_name = 'summary' AND s.seq_in_index = 2 THEN 1 ELSE 0 END) = 1
                      AND SUM(CASE WHEN s.column_name = 'content' AND s.seq_in_index = 3 THEN 1 ELSE 0 END) = 1
                     THEN 1 ELSE 0
                   END
              FROM information_schema.statistics s
             WHERE s.table_schema = DATABASE()
               AND s.table_name = 'articles'
               AND s.index_name = #{indexName}
            """)
    boolean hasFullTextIndex(@Param("indexName") String indexName);
}