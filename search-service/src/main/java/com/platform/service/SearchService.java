package com.platform.service;

import com.platform.dto.resp.SearchResp;

/**
 * 搜索业务接口，定义对外暴露的服务能力。
 */

public interface SearchService {

    /**
     * Search approved article projections by keyword.
     *
     * <p>The current implementation searches the Elasticsearch article projection
     * index and matches the keyword against article title and summary.</p>
     *
     * @param keyword search keyword
     * @param page page number, starting from 1
     * @param pageSize page size
     * @return paged search results
     */
    SearchResp search(String keyword, int page, int pageSize);
}
