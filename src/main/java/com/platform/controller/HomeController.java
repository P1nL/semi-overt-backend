package com.platform.controller;

import com.platform.dto.resp.HomeResp;
import com.platform.service.HomeService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合接口
 */
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 获取首页聚合数据
     * GET /api/v1/home
     *
     * 公开接口，无需登录。
     * 返回 Hero 主/次卡 + 三个阅读时长分类的最新文章列表。
     */
    @GetMapping
    public Result<HomeResp> getHome() {
        return Result.ok(homeService.getHomeData());
    }
}