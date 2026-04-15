package com.platform.content.util;

import com.platform.kernel.enums.DurationCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 閺傚洨鐝峰銉ュ徔缁紮绱濋幓鎰返閸欘垰顦查悽銊ф畱鏉堝懎濮弬瑙勭《閵? */

public class ArticleUtils {

    private static final int WORDS_PER_MINUTE = 300;
    private static final String MARKDOWN_LINK_PATTERN = "\\[([^\\]]*)]\\([^)]*\\)";
    private static final String MARKDOWN_IMAGE_PATTERN = "!\\[[^\\]]*]\\([^)]*\\)";

    /**
     * 閹笛嗩攽words閵?     */
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
     * 閹笛嗩攽readminutes閵?     */
    public static BigDecimal calcReadMinutes(int wordCount) {
        if (wordCount <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(wordCount)
                .divide(BigDecimal.valueOf(WORDS_PER_MINUTE), 1, RoundingMode.HALF_UP);
    }

    /**
     * 閹笛嗩攽閺冨爼鏆遍崚鍡欒閵?     */
    public static DurationCategory calcDurationCategory(BigDecimal readMinutes) {
        if (readMinutes == null) return DurationCategory.QUICK;
        double minutes = readMinutes.doubleValue();
        if (minutes < 3.0) return DurationCategory.QUICK;
        if (minutes <= 10.0) return DurationCategory.SHORT;
        return DurationCategory.DEEP;
    }

    // Preview only uses the first non-empty plain-text paragraph.
    /**
     * 閹绘劕褰噋reviewtext閵?     */
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
            String preview = toPlainTextPreview(trimmedBlock, maxLength);
            if (!preview.isEmpty()) {
                return preview;
            }
        }

        return toPlainTextPreview(normalized, maxLength);
    }

    /**
     * 閹笛嗩攽plaintextpreview閵?     */
    private static String toPlainTextPreview(String block, int maxLength) {
        String plain = block
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll(MARKDOWN_IMAGE_PATTERN, " ")
                .replaceAll(MARKDOWN_LINK_PATTERN, "$1")
                .replaceAll("<\\/?(?:img|hr)[^>]*>", " ")
                .replaceAll("<\\/?(?:p|div|section|article|blockquote|pre|code|ul|ol|li|h[1-6]|mark)[^>]*>", " ")
                .replaceAll("`", "")
                .replaceAll("(?m)^[\\t ]{0,3}(#{1,6}|>|-|\\+|\\*|\\d+\\.)[\\t ]+", "")
                .replaceAll("[*_~]", "")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
        if (plain.isEmpty()) {
            return "";
        }

        return plain.length() > maxLength ? plain.substring(0, maxLength) + "..." : plain;
    }
}



