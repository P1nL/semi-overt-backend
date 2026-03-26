package com.platform.service.impl;

import com.platform.common.event.ArticleStatusChangedEvent;
import com.platform.document.ArticleSearchDocument;
import com.platform.enums.ArticleStatus;
import com.platform.repository.ArticleSearchRepository;
import com.platform.service.SearchEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchEventServiceImpl implements SearchEventService {

    private final ArticleSearchRepository articleSearchRepository;

    @Override
    public void handleArticleStatusChanged(ArticleStatusChangedEvent event) {
        if (event.getToStatus() == ArticleStatus.APPROVED) {
            articleSearchRepository.save(ArticleSearchDocument.builder()
                    .articleId(event.getArticleId())
                    .authorId(event.getAuthorId())
                    .title(event.getTitle())
                    .summary(event.getSummary())
                    .coverUrl(event.getCoverUrl())
                    .coverColor(event.getCoverColor())
                    .readMinutes(event.getReadMinutes())
                    .durationCategory(event.getDurationCategory())
                    .publishedAt(event.getPublishedAt())
                    .updatedAt(event.getUpdatedAt())
                    .build());
            return;
        }
        articleSearchRepository.deleteById(event.getArticleId());
    }
}
