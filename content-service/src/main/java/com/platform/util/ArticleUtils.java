package com.platform.util;

import com.platform.enums.DurationCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 文章工具类，提供可复用的辅助方法。
 */

public class ArticleUtils {

    private static final int WORDS_PER_MINUTE = 300;

    /**
     * 执行words。
     */
    public static int countWords(String content) {
        if (content == null || content.isBlank()) return 0;

        String plain = content
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`[^`]*`", "")
                .replaceAll("!?\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#*_~>|\\-=+\\[\\]{}()\\\\]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.isEmpty() ? 0 : plain.length();
    }

    /**
     * 执行readminutes。
     */
    public static BigDecimal calcReadMinutes(int wordCount) {
        if (wordCount <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(wordCount)
                .divide(BigDecimal.valueOf(WORDS_PER_MINUTE), 1, RoundingMode.HALF_UP);
    }

    /**
     * 执行时长分类。
     */
    public static DurationCategory calcDurationCategory(BigDecimal readMinutes) {
        if (readMinutes == null) return DurationCategory.QUICK;
        double minutes = readMinutes.doubleValue();
        if (minutes < 3.0) return DurationCategory.QUICK;
        if (minutes <= 10.0) return DurationCategory.SHORT;
        return DurationCategory.DEEP;
    }

    // Preview only uses the first non-empty plain-text paragraph.
    /**
     * 提取previewtext。
     */
    public static String extractPreviewText(String content, int maxLength) {
        if (content == null || content.isBlank()) return "";

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) return "";

        String[] blocks = normalized.split("\\n\\s*\\n");
        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) {
                continue;
            }
            return toPlainTextPreview(trimmedBlock, maxLength);
        }

        return "";
    }

    /**
     * 执行plaintextpreview。
     */
    private static String toPlainTextPreview(String block, int maxLength) {
        String[] lines = block.split("\\n");
        StringBuilder plainBuilder = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            if (!isPlainTextLine(trimmedLine)) {
                return "";
            }
            if (plainBuilder.length() > 0) {
                plainBuilder.append(' ');
            }
            plainBuilder.append(trimmedLine);
        }

        String plain = plainBuilder.toString().replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) {
            return "";
        }

        return plain.length() > maxLength ? plain.substring(0, maxLength) + "..." : plain;
    }

    /**
     * 判断plaintextline。
     */
    private static boolean isPlainTextLine(String line) {
        return !line.startsWith("#")
                && !line.startsWith(">")
                && !line.startsWith("- ")
                && !line.startsWith("* ")
                && !line.startsWith("+ ")
                && !line.matches("\\d+\\.\\s+.*")
                && !line.startsWith("```")
                && !line.startsWith("|")
                && !line.startsWith("![")
                && !line.startsWith("[")
                && !line.startsWith("---")
                && !line.startsWith("***")
                && !line.startsWith("___")
                && !line.contains("`")
                && !line.contains("![")
                && !line.contains("](")
                && !line.matches("https?://\\S+");
    }
}
