package com.platform.search.service;

import com.platform.search.api.resp.SearchResp;


public interface SearchService {

    /**
     * Search approved article projections by keyword.
     *
     * <p>The current implementation searches approved articles from MySQL and
     * matches the keyword against article title and summary.</p>
     *
     * @param keyword search keyword
     * @param page page number, starting from 1
     * @param pageSize page size
     * @return paged search results
     */
    SearchResp search(String keyword, int page, int pageSize);
}


