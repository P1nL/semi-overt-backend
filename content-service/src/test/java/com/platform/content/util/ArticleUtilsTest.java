package com.platform.content.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleUtilsTest {

    @Test
    void extractPreviewText_keepsHeadingTextWhenContentStartsWithHeading() {
        String content = "## 二级标题\n\n正文第一段";

        String preview = ArticleUtils.extractPreviewText(content, 120);

        assertThat(preview).isEqualTo("二级标题");
    }

    @Test
    void extractPreviewText_skipsMarkupOnlyBlockAndFallsBackToNextMeaningfulBlock() {
        String content = "![](https://img.example.com/a.png)\n\n第二段正文";

        String preview = ArticleUtils.extractPreviewText(content, 120);

        assertThat(preview).isEqualTo("第二段正文");
    }
}
