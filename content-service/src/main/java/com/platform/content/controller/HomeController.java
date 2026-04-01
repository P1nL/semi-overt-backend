package com.platform.content.controller;

import com.platform.content.api.resp.HomeResp;
import com.platform.content.service.HomeService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

        @GetMapping
    public Result<HomeResp> getHome() {
        return Result.ok(homeService.getHomeData());
    }
}


