package com.platform.search.util;

public final class ArticlePreviewUtils {

    private static final String MARKDOWN_LINK_PATTERN = "\\[([^\\]]*)]\\([^)]*\\)";
    private static final String MARKDOWN_IMAGE_PATTERN = "!\\[[^\\]]*]\\([^)]*\\)";

    private ArticlePreviewUtils() {
    }

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
