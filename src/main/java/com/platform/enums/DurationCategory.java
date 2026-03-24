package com.platform.enums;

/**
 * 阅读时长分类枚举
 * 计算规则：word_count / 300（分钟）
 * QUICK  < 3 分钟（速读）
 * SHORT  3~10 分钟（短读）
 * DEEP   > 10 分钟（深读）
 */
public enum DurationCategory {
    QUICK,
    SHORT,
    DEEP
}