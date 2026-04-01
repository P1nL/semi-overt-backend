package com.platform.search.mapper;

import com.platform.search.model.SearchArticleRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SearchArticleMapper {

    long countByKeyword(@Param("escapedKeyword") String escapedKeyword);

    List<SearchArticleRow> searchByKeyword(@Param("escapedKeyword") String escapedKeyword,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);
}


