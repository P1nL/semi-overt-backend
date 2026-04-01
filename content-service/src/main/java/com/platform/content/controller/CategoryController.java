package com.platform.content.controller;

import com.platform.content.api.resp.CategoryResp;
import com.platform.content.service.CategoryService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

        @GetMapping("/{category}/articles")
    public Result<CategoryResp> listArticles(
            @PathVariable String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        return Result.ok(categoryService.listByCategory(category, page, pageSize));
    }
}


