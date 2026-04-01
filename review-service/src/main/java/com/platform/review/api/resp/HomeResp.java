package com.platform.review.api.resp;

import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 首页聚合响应。
 */
@Data
@Builder
public class HomeResp {

    private HeroData hero;
    private List<SectionData> sections;

    @Data
    @Builder
    public static class HeroData {
        private ArticleCardResp primary;
        private List<ArticleCardResp> secondary;
    }

    @Data
    @Builder
    public static class SectionData {
        private DurationCategory category;
        private List<ArticleCardResp> list;
    }
}
