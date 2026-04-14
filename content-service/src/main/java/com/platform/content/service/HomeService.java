package com.platform.content.service;

import com.platform.content.api.resp.HomeResp;


public interface HomeService {

    HomeResp getHomeData();

    /**
     * 清除当天首页 Hero 缓存，触发下次请求重新选取。
     * 文章状态变为 APPROVED 时调用，使新通过的文章有机会出现在首页。
     */
    void invalidateHeroCache();
}


