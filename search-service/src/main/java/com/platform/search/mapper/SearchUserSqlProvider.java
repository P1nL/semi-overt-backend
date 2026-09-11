package com.platform.search.mapper;

import com.platform.search.model.SearchKeyword;

import java.util.Map;

/** Parameterized SQL for username/nickname-only public user search. */
public final class SearchUserSqlProvider {

    private static final String USER_SEPARATORS = "[-_./]+";
    private static final String WHITESPACE = "\\s+";

    public String count(Map<String, Object> parameters) {
        SearchKeyword query = query(parameters);
        return "SELECT COUNT(*) FROM (" + baseSql() + ") searchable WHERE " + predicate(query);
    }

    public String search(Map<String, Object> parameters) {
        SearchKeyword query = query(parameters);
        return "SELECT id, username, nickname, avatar_url FROM (" + baseSql() + ") searchable "
                + "WHERE " + predicate(query) + " ORDER BY " + bucket(query) + ", " + score(query) + " DESC"
                + ", id DESC LIMIT #{limit} OFFSET #{offset}";
    }

    private static SearchKeyword query(Map<String, Object> parameters) {
        Object value = parameters.get("query");
        if (!(value instanceof SearchKeyword query)) {
            throw new IllegalArgumentException("Search query is required");
        }
        return query;
    }

    private static String baseSql() {
        return "SELECT u.id, u.username, u.nickname, u.avatar_url, "
                + clean("COALESCE(u.username, '')") + " AS search_username, "
                + clean("COALESCE(u.nickname, '')") + " AS search_nickname "
                + "FROM users u WHERE COALESCE(u.username, '') <> ''";
    }

    private static String predicate(SearchKeyword query) {
        if (query.isBlank()) {
            return "1 = 0";
        }
        StringBuilder sql = new StringBuilder("((search_username LIKE #{query.phraseLike} ESCAPE '\\\\'"
                + " OR search_nickname LIKE #{query.phraseLike} ESCAPE '\\\\')");
        if (query.isMultiTerm()) {
            sql.append(" OR (");
            for (int i = 0; i < query.getTerms().size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                String ref = "#{query.terms[" + i + "].like}";
                sql.append("(search_username LIKE ").append(ref).append(" ESCAPE '\\\\'"
                        + " OR search_nickname LIKE ").append(ref).append(" ESCAPE '\\\\')");
            }
            sql.append(")");
        }
        return sql.append(")").toString();
    }

    private static String bucket(SearchKeyword query) {
        if (query.isBlank()) {
            return "7";
        }
        String p = "#{query.phrase}";
        String pl = "#{query.phrasePrefixLike}";
        String pll = "#{query.phraseLike}";
        return "CASE WHEN search_username = " + p + " THEN 0"
                + " WHEN search_username LIKE " + pl + " ESCAPE '\\\\' THEN 1"
                + " WHEN search_username LIKE " + pll + " ESCAPE '\\\\' THEN 2"
                + " WHEN search_nickname = " + p + " THEN 3"
                + " WHEN search_nickname LIKE " + pl + " ESCAPE '\\\\' THEN 4"
                + " WHEN search_nickname LIKE " + pll + " ESCAPE '\\\\' THEN 5"
                + " ELSE 6 END";
    }

    private static String score(SearchKeyword query) {
        if (query.isBlank()) {
            return "0";
        }
        String phrase = "#{query.phraseLike}";
        StringBuilder sql = new StringBuilder("(CASE WHEN search_username LIKE ").append(phrase)
                .append(" ESCAPE '\\\\' THEN 12 ELSE 0 END")
                .append(" + CASE WHEN search_nickname LIKE ").append(phrase)
                .append(" ESCAPE '\\\\' THEN 6 ELSE 0 END");
        for (int i = 0; i < query.getTerms().size(); i++) {
            String ref = "#{query.terms[" + i + "].like}";
            sql.append(" + CASE WHEN search_username LIKE ").append(ref)
                    .append(" ESCAPE '\\\\' THEN 4 ELSE 0 END")
                    .append(" + CASE WHEN search_nickname LIKE ").append(ref)
                    .append(" ESCAPE '\\\\' THEN 2 ELSE 0 END");
        }
        return sql.append(")").toString();
    }

    private static String clean(String expression) {
        String value = expression;
        value = regexReplace(value, USER_SEPARATORS, " ");
        value = regexReplace(value, WHITESPACE, " ");
        return "TRIM(LOWER(" + value + "))";
    }

    private static String regexReplace(String expression, String pattern, String replacement) {
        return "REGEXP_REPLACE(" + expression + ", " + sqlLiteral(pattern) + ", " + sqlLiteral(replacement) + ")";
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }
}