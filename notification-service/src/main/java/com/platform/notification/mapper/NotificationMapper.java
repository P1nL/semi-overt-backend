package com.platform.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.notification.api.resp.NotificationResp;
import com.platform.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("""
            SELECT id, user_id, type, title, content, biz_id, read_status,
                   decision_id, created_at
              FROM notifications
             WHERE decision_id = #{decisionId}
             FOR SHARE
            """)
    Notification findByDecisionId(@Param("decisionId") String decisionId);

    /**
     * Select only the six fields exposed by the legacy monolith endpoint.
     * Existing rows with NULL compatibility metadata remain readable without
     * inferring values from their text.
     */
    @Select("""
            SELECT id, user_id, type, title, content, created_at
              FROM notifications
             WHERE user_id = #{userId}
             ORDER BY created_at DESC, id DESC
             LIMIT #{limit}
            """)
    @Results(id = "notificationResponse", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "type", property = "type"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<NotificationResp> findByUserId(@Param("userId") Long userId,
                                        @Param("limit") int limit);
}