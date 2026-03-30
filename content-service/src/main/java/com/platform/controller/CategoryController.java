package com.platform.controller;

import com.platform.dto.resp.CategoryResp;
import com.platform.service.CategoryService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分类控制器，对外提供相关 HTTP 接口。
 */

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 分页查询指定分类的文章列表
     * GET /api/v1/categories/{category}/articles
     *
     * 公开接口，无需登录。
     *
     * @param category 阅读时长分类：QUICK / SHORT / DEEP（大小写不敏感）
     * @param page     页码，默认 1
     * @param pageSize 每页条数，默认 10
     */
    @GetMapping("/{category}/articles")
    public Result<CategoryResp> listArticles(
            @PathVariable String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(categoryService.listByCategory(category, page, pageSize));
    }
}