package com.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.platform.enums.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 users 表
 */
@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 唯一用户名，4~20位，字母数字下划线，不能纯数字 */
    private String username;

    /** 显示昵称，可随时修改，用于页面展示；注册时默认与 username 相同 */
    private String nickname;

    /** 唯一邮箱，支持登录/找回密码 */
    private String email;

    /** BCrypt 哈希密码（禁止明文） */
    private String password;

    /** 用户角色 */
    private UserRole role;

    /** 头像访问 URL（相对路径，如 /static/uploads/...） */
    private String avatarUrl;

    /** 个人主页封面访问 URL */
    private String coverUrl;

    /** 个性签名，建议 <= 50 字 */
    private String signature;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}