package com.platform.content.service;

import com.platform.content.api.resp.HomeResp;

public interface HomeService {

    HomeResp getHomeData(Long currentUserId);

    /** Compatibility overload for existing service tests/callers; null means anonymous. */
    default HomeResp getHomeData() {
        return getHomeData(null);
    }

    /** S3 approval hook; package 1 does not use a global hero rotation cache. */
    void invalidateHeroCache();
}
