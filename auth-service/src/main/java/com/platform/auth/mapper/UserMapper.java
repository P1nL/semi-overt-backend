package com.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM users WHERE id = #{userId} FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE users SET password = #{password}, session_version = session_version + 1, updated_at = NOW() WHERE id = #{userId}")
    int updatePasswordAndRotateSession(@Param("userId") Long userId, @Param("password") String password);

    @Update("""
            UPDATE users
               SET nickname = #{nickname},
                   avatar_url = #{avatarUrl},
                   cover_url = #{coverUrl},
                   signature = #{signature},
                   updated_at = NOW(6)
             WHERE id = #{userId}
            """)
    int updateProfileFields(@Param("userId") Long userId,
                            @Param("nickname") String nickname,
                            @Param("avatarUrl") String avatarUrl,
                            @Param("coverUrl") String coverUrl,
                            @Param("signature") String signature);
}
