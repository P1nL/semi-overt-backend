package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;

/**
 * 搜索事件业务接口，定义对外暴露的服务能力。
 */

public interface SearchEventService {

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
