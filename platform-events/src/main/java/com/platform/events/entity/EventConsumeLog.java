package com.platform.events.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件消费日志实体，对应消费者幂等记录与处理状态。
 */

@Data
@TableName("event_consume_log")
public class EventConsumeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;
    private String consumer;
    private String status;
    private LocalDateTime consumedAt;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
