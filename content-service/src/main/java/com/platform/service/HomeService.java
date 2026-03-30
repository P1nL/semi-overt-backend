package com.platform.service;

import com.platform.dto.resp.HomeResp;

/**
 * 首页业务接口，定义对外暴露的服务能力。
 */

public interface HomeService {

    /**
     * 获取首页聚合数据
     * 包含 Hero 主/次卡 + 三个阅读时长分类 section
     * 仅返回 APPROVED 状态的文章，无需登录
     */
    HomeResp getHomeData();
}