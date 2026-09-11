package com.platform.search.mapper;

import com.platform.search.model.SearchKeyword;
import com.platform.search.model.SearchUserRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

/** Read-only public user projection; no auth-service write or contract change. */
@Mapper
public interface SearchUserMapper {

    @SelectProvider(type = SearchUserSqlProvider.class, method = "count")
    long countByKeyword(@Param("query") SearchKeyword query);

    @SelectProvider(type = SearchUserSqlProvider.class, method = "search")
    @Results(id = "searchUserRow", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "username", property = "username"),
            @Result(column = "nickname", property = "nickname"),
            @Result(column = "avatar_url", property = "avatarUrl")
    })
    List<SearchUserRow> searchByKeyword(@Param("query") SearchKeyword query,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);
}