package com.platform.dto.resp;

import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * HomeResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class HomeResp {

    /** Hero 区域数据 */
    private HeroData hero;

    /** 内容区分类 section 列表，固定顺序：QUICK → SHORT → DEEP */
    private List<SectionData> sections;

    // ==================== 内部类 ====================

    @Data
    @Builder
    public static class HeroData {

        /** Hero 主卡：最新 1 篇 APPROVED 文章 */
        private ArticleCardResp primary;

        /** Hero 次卡：次新最多 4 篇 APPROVED 文章 */
        private List<ArticleCardResp> secondary;
    }

    @Data
    @Builder
    public static class SectionData {

        /** 阅读时长分类 */
        private DurationCategory category;

        /** 该分类下的文章卡片列表（首页固定取 6 篇，不分页） */
        private List<ArticleCardResp> list;
    }
}