-- V2: 为 articles 表添加 last_featured_at 字段，用于首页卡片公平轮替逻辑
-- 语义：记录文章最近一次被选入首页 Hero 的时间；NULL 表示该文章尚未上过首页

ALTER TABLE articles
    ADD COLUMN last_featured_at DATETIME NULL DEFAULT NULL COMMENT 'last time this article was shown on home hero';

CREATE INDEX idx_articles_featured_status ON articles (status, last_featured_at);
