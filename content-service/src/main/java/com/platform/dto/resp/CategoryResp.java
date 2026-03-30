package com.platform.dto.resp;

import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * CategoryResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class CategoryResp {

    /** 阅读时长分类 */
    private DurationCategory category;

    /** 当前页文章卡片列表 */
    private List<ArticleCardResp> list;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    /** 总页数 */
    private Long pages;
}