package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.entity.Article;
import com.platform.enums.DurationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Historical single-app article mapper residue.
 *
 * <p>This root src tree is not part of the active multi-module runtime. Search in
 * the current system is provided by search-service backed by Elasticsearch. The
 * SQL search method below is retained only as legacy reference.</p>
 */
@Deprecated
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 首页 Hero 主卡：取最新 1 篇 APPROVED 文章（含作者信息）
     * 仅作为随机缓存失效时的兜底方法，正常路径走 selectRandomApproved
     */
    Article selectHeroPrimary();

    /**
     * 首页 Hero 次卡：取次新 4 篇 APPROVED 文章（含作者信息）
     * 仅作为随机缓存失效时的兜底方法，正常路径走 selectRandomApproved
     */
    List<Article> selectHeroSecondary();

    /**
     * 随机取 N 篇 APPROVED 文章，用于首页 Hero 每日随机逻辑
     * 实现：ORDER BY RAND() LIMIT #{limit}
     * 说明：平台定位为小圈子，数据量小，RAND() 性能可接受；
     *       数据量增大时可改为「随机偏移量」或应用层随机
     *
     * @param limit 返回条数（首页 Hero 取 5：1 主卡 + 4 次卡）
     */
    List<Article> selectRandomApproved(@Param("limit") int limit);

    /**
     * 按阅读时长分类查询 APPROVED 文章，用于首页各分类 section
     *
     * @param category 阅读时长分类：QUICK / SHORT / DEEP
     * @param limit    返回条数（首页各 section 取固定条数，不分页）
     */
    List<Article> selectApprovedByCategory(
            @Param("category") DurationCategory category,
            @Param("limit") int limit
    );

    /**
     * 分类详情页：分页查询指定分类的 APPROVED 文章
     *
     * @param page     MyBatis Plus 分页对象
     * @param category 阅读时长分类
     */
    IPage<Article> selectPageByCategory(
            Page<Article> page,
            @Param("category") DurationCategory category
    );

    /**
     * Legacy database LIKE search retained in the historical single-app residue.
     *
     * @param page    分页对象
     * @param keyword 搜索关键词
     */
    IPage<Article> searchByKeyword(
            Page<Article> page,
            @Param("keyword") String keyword
    );
}
