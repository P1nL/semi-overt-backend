package com.platform.service;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.common.event.ArticleSubmittedEvent;

/**
 * 审核事件业务接口，定义对外暴露的服务能力。
 */

public interface ReviewEventService {

    void projectPendingTask(ArticleSubmittedEvent event);

    void handleArticleStatusChanged(ArticleStatusChangedEvent event);
}
