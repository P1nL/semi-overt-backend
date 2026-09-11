package com.platform.search.model;

import lombok.Data;

/** Public-only user projection; private auth fields are intentionally absent. */
@Data
public class SearchUserRow {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
}