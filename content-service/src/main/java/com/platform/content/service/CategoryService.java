package com.platform.content.service;

import com.platform.content.api.resp.CategoryResp;


public interface CategoryService {

        CategoryResp listByCategory(String category, int page, int pageSize);
}


