package com.platform.service;

import com.platform.dto.resp.CategoryResp;

/**
 * 分类服务接口
 */
public interface CategoryService {

    /**
     * 分页查询指定阅读时长分类的 APPROVED 文章
     *
     * @param category 分类字符串：QUICK / SHORT / DEEP（大小写不敏感）
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分类文章列表（含分页元数据）
     * @throws com.platform.exception.BusinessException 400 若 category 不合法
     */
    CategoryResp listByCategory(String category, int page, int pageSize);
}