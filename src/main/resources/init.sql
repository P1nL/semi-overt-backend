-- 内容创作平台数据库初始化脚本
-- 数据库版本：MySQL 8.0+
-- 字符集：utf8mb4

CREATE DATABASE IF NOT EXISTS content_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE content_platform;

-- ===================== 用户表 =====================
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(20)     NOT NULL                COMMENT '唯一用户名，4~20位',
    email       VARCHAR(100)    NOT NULL                COMMENT '唯一邮箱',
    password    VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 哈希密码',
    role        ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER' COMMENT '用户角色',
    avatar_url  VARCHAR(255)                            COMMENT '头像访问URL（相对路径）',
    cover_url   VARCHAR(255)                            COMMENT '个人主页封面URL',
    signature   VARCHAR(100)                            COMMENT '个性签名',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ===================== 文章表 =====================
CREATE TABLE IF NOT EXISTS articles (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    author_id           BIGINT          NOT NULL                COMMENT '作者ID',
    title               VARCHAR(120)                            COMMENT '标题，草稿可为空',
    content             LONGTEXT                                COMMENT 'Markdown正文（正式内容，草稿期间见Redis）',
    summary             VARCHAR(255)                            COMMENT '摘要',
    cover_url           VARCHAR(255)                            COMMENT '封面图URL',
    cover_color         VARCHAR(16)                             COMMENT '封面主色（氛围色）',
    word_count          INT             NOT NULL DEFAULT 0      COMMENT '字数',
    read_minutes        DECIMAL(5,1)    NOT NULL DEFAULT 0.0    COMMENT '阅读时长（分钟）',
    duration_category   ENUM('QUICK','SHORT','DEEP') NOT NULL DEFAULT 'QUICK' COMMENT '阅读时长分类',
    status              ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL DEFAULT 'DRAFT' COMMENT '文章状态',
    submit_count        INT             NOT NULL DEFAULT 0      COMMENT '累计提交审核次数',
    last_submitted_at   DATETIME                                COMMENT '最近一次提交时间',
    published_at        DATETIME                                COMMENT '审核通过时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '逻辑删除（0正常1已删除）',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_author_id (author_id),
    KEY idx_status (status),
    KEY idx_duration_category (duration_category),
    KEY idx_published_at (published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章表';

-- ===================== 审核日志表 =====================
CREATE TABLE IF NOT EXISTS review_logs (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    article_id  BIGINT      NOT NULL                COMMENT '文章ID',
    operator_id BIGINT      NOT NULL                COMMENT '操作人ID',
    action      ENUM('APPROVE','REJECT','RETURN','CANCEL') NOT NULL COMMENT '审核动作',
    from_status ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL COMMENT '操作前状态',
    to_status   ENUM('DRAFT','PENDING','APPROVED','RETURNED','REJECTED') NOT NULL COMMENT '操作后状态',
    reason      VARCHAR(500)                        COMMENT '退回/拒绝原因',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_article_id (article_id),
    KEY idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核日志表';

-- ===================== 测试数据（可选） =====================
-- 初始管理员账号（密码：admin123456，已用BCrypt加密）
-- 实际使用时请修改密码
INSERT IGNORE INTO users (username, email, password, role)
VALUES ('admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'ADMIN');