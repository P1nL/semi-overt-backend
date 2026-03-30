package com.platform.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知投递实体，映射持久化层中的相关数据记录。
 */

@Data
@TableName("notification_deliveries")
public class NotificationDelivery {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long notificationId;
    private String channel;
    private String status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
