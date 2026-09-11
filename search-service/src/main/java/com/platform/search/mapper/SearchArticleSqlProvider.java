package com.platform.search.mapper;

import com.platform.search.model.SearchKeyword;

import java.util.Map;

/**
 * Generates bounded, parameterized MySQL SQL. Search rows are filtered and
 * ranked in the database; the service never loads the whole matching corpus.
 */
public final class SearchArticleSqlProvider {

    private static final String HIDDEN_BLOCKS = "(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>";
    private static final String HTML_BLOCK_TAGS = "(?is)</?(?:address|article|aside|blockquote|br|caption|dd|details|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|li|main|nav|ol|p|pre|section|table|tbody|td|tfoot|th|thead|tr|ul)[^>]*>";
    private static final String HTML_TAGS = "<[^>]+>";
    private static final String HTML_HEADING_OUTSIDE = "(?is)(?:^|</h[1-6]>).*?(?=<h[1-6][^>]*>|$)";
    private static final String HTML_HEADING_TAGS = "(?is)</?h[1-6][^>]*>";
    private static final String MARKDOWN_HEADING_LINE = "(?m)^\\s{0,3}[#]{1,6}\\s+([^\\r\\n]+)";
    private static final String MARKDOWN_NON_HEADING_LINE = "(?m)^(?!\\s{0,3}[#]{1,6}\\s+).*(?:\\r?\\n|$)";
    private static final String MARKDOWN_LINK_SUFFIX = "\\]\\([^)]*\\)";
    private static final String MARKDOWN_REFERENCE_SUFFIX = "\\]\\[[^\\]]*\\]";
    private static final String RAW_URL = "(https?://|www\\.)\\S+";
    private static final String MARKDOWN_INLINE_FORMAT = "[*_`~]+";
    private static final String SEARCH_SEPARATORS = "[-_./|]+";
    private static final String WHITESPACE = "\\s+";

    public String count(Map<String, Object> parameters) {
        SearchKeyword query = query(parameters);
        boolean fullText = Boolean.TRUE.equals(parameters.get("fullText"));
        return "SELECT COUNT(*) FROM (" + baseSql(fullText) + ") searchable WHERE "
                + predicate(query, fullText);
    }

    public String search(Map<String, Object> parameters) {
        SearchKeyword query = query(parameters);
        boolean fullText = Boolean.TRUE.equals(parameters.get("fullText"));

        return "SELECT article_id, author_id, title, summary, content, cover_url, cover_color, "
                + "word_count, read_minutes, duration_category, status, published_at, updated_at "
                + "FROM (" + baseSql(fullText) + ") searchable "
                + "WHERE " + predicate(query, fullText)
                + " ORDER BY " + bucket(query) + ", " + score(query) + " DESC"
                + ", fulltext_score DESC, COALESCE(published_at, updated_at, created_at) DESC, article_id DESC"
                + " LIMIT #{limit} OFFSET #{offset}";
    }

    private static SearchKeyword query(Map<String, Object> parameters) {
        Object value = parameters.get("query");
        if (!(value instanceof SearchKeyword query)) {
            throw new IllegalArgumentException("Search query is required");
        }
        return query;
    }

    private static String baseSql(boolean fullText) {
        String title = clean("COALESCE(a.title, '')");
        String summary = clean("COALESCE(a.summary, '')");
        String content = clean("COALESCE(a.content, '')");
        String headings = cleanHeadings("COALESCE(a.content, '')");
        String fullTextScore = fullText
                ? "MATCH(a.title, a.summary, a.content) AGAINST (#{query.phrase} IN NATURAL LANGUAGE MODE)"
                : "0";

        return "SELECT a.id AS article_id, a.author_id, a.title, a.summary, a.content, "
                + "a.cover_url, a.cover_color, a.word_count, a.read_minutes, a.duration_category, a.status, "
                + "a.published_at, a.updated_at, a.created_at, "
                + title + " AS search_title, "
                + summary + " AS search_summary, "
                + content + " AS search_content, "
                + headings + " AS search_headings, "
                + fullTextScore + " AS fulltext_score "
                + "FROM articles a "
                + "WHERE a.status = 'APPROVED' AND COALESCE(a.deleted, 0) = 0";
    }

    private static String predicate(SearchKeyword query, boolean fullText) {
        if (query.isBlank()) {
            return "1 = 0";
        }

        StringBuilder sql = new StringBuilder();
        // Optional FULLTEXT is a ranking hint, not a recall gate: token size,
        // stop words and partial phrases must not remove cleaned LIKE matches.
        sql.append("((search_title LIKE #{query.phraseLike} ESCAPE '\\\\'")
                .append(" OR search_summary LIKE #{query.phraseLike} ESCAPE '\\\\'");
        if (query.isPhraseContentMatchAllowed()) {
            sql.append(" OR search_content LIKE #{query.phraseLike} ESCAPE '\\\\'");
        }
        sql.append(")");

        if (query.isMultiTerm()) {
            sql.append(" OR (");
            for (int i = 0; i < query.getTerms().size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                appendTermClause(sql, query, i);
            }
            sql.append(")");
        }
        return sql.append(")").toString();
    }

    private static void appendTermClause(StringBuilder sql, SearchKeyword query, int index) {
        String ref = "#{query.terms[" + index + "].like}";
        sql.append("(search_title LIKE ").append(ref).append(" ESCAPE '\\\\'")
                .append(" OR search_summary LIKE ").append(ref).append(" ESCAPE '\\\\'");
        if (query.getTerms().get(index).isContentMatchAllowed()) {
            sql.append(" OR search_content LIKE ").append(ref).append(" ESCAPE '\\\\'");
        }
        sql.append(")");
    }

    private static String bucket(SearchKeyword query) {
        if (query.isBlank()) {
            return "9";
        }
        String p = "#{query.phrase}";
        String pl = "#{query.phrasePrefixLike}";
        String pll = "#{query.phraseLike}";
        StringBuilder sql = new StringBuilder("CASE"
                + " WHEN search_title = " + p + " THEN 0"
                + " WHEN search_title LIKE " + pl + " ESCAPE '\\\\' THEN 1"
                + " WHEN search_title LIKE " + pll + " ESCAPE '\\\\' THEN 2"
                + " WHEN search_summary = " + p + " THEN 3"
                + " WHEN search_summary LIKE " + pl + " ESCAPE '\\\\' THEN 4"
                + " WHEN search_summary LIKE " + pll + " ESCAPE '\\\\' THEN 5"
                + " WHEN search_headings LIKE " + pll + " ESCAPE '\\\\' THEN 6");
        if (query.isPhraseContentMatchAllowed()) {
            sql.append(" WHEN search_content LIKE ").append(pll).append(" ESCAPE '\\\\' THEN 7");
        }
        return sql.append(" ELSE 8 END").toString();
    }

    private static String score(SearchKeyword query) {
        if (query.isBlank()) {
            return "0";
        }
        String phrase = "#{query.phraseLike}";
        StringBuilder sql = new StringBuilder("(CASE WHEN search_title LIKE ").append(phrase)
                .append(" ESCAPE '\\\\' THEN 24 ELSE 0 END")
                .append(" + CASE WHEN search_summary LIKE ").append(phrase)
                .append(" ESCAPE '\\\\' THEN 12 ELSE 0 END")
                .append(" + CASE WHEN search_headings LIKE ").append(phrase)
                .append(" ESCAPE '\\\\' THEN 8 ELSE 0 END");
        if (query.isPhraseContentMatchAllowed()) {
            sql.append(" + CASE WHEN search_content LIKE ").append(phrase)
                    .append(" ESCAPE '\\\\' THEN 4 ELSE 0 END");
        }
        for (int i = 0; i < query.getTerms().size(); i++) {
            String ref = "#{query.terms[" + i + "].like}";
            sql.append(" + CASE WHEN search_title LIKE ").append(ref)
                    .append(" ESCAPE '\\\\' THEN 6 ELSE 0 END")
                    .append(" + CASE WHEN search_summary LIKE ").append(ref)
                    .append(" ESCAPE '\\\\' THEN 3 ELSE 0 END");
            if (query.getTerms().get(i).isContentMatchAllowed()) {
                sql.append(" + CASE WHEN search_content LIKE ").append(ref)
                        .append(" ESCAPE '\\\\' THEN 1 ELSE 0 END");
            }
        }
        return sql.append(")").toString();
    }

    /** Extracts all visible HTML and Markdown headings; hidden script/style blocks are removed first. */
    private static String cleanHeadings(String expression) {
        String withoutHidden = regexReplace(expression, HIDDEN_BLOCKS, " ");
        // MySQL REGEXP_REPLACE with a capture-or-dot pattern keeps every
        // visible heading body and discards all non-heading HTML text. This
        // handles multiple headings and cannot see script/style content.
        String htmlHeadingBodies = regexReplace(
                withoutHidden,
                "(?is)<h[1-6][^>]*>(.*?)</h[1-6]>|.",
                "$1");
        String markdownHeadingBodies = regexReplace(
                withoutHidden,
                "(?ms)^\\s{0,3}[#]{1,6}\\s+([^\\r\\n]+)|^(?!\\s{0,3}[#]{1,6}\\s+).*(?:\\r?\\n|$)",
                "$1");
        return "CONCAT(" + clean(htmlHeadingBodies) + ", ' ', " + clean(markdownHeadingBodies) + ")";
    }
    private static String clean(String expression) {
        String value = expression;
        value = regexReplace(value, HIDDEN_BLOCKS, " ");
        value = regexReplace(value, HTML_BLOCK_TAGS, " ");
        value = regexReplace(value, HTML_TAGS, "");
        value = regexReplace(value, MARKDOWN_LINK_SUFFIX, "]");
        value = regexReplace(value, MARKDOWN_REFERENCE_SUFFIX, "]");
        value = replace(value, "&nbsp;", " ");
        value = replace(value, "&#160;", " ");
        value = replace(value, "&amp;", "&");
        value = replace(value, "&lt;", "<");
        value = replace(value, "&gt;", ">");
        value = replace(value, "&quot;", "\"");
        value = replace(value, "&#39;", "'");
        value = regexReplace(value, RAW_URL, " ");
        value = regexReplace(value, MARKDOWN_INLINE_FORMAT, "");
        value = replace(value, "[", "");
        value = replace(value, "]", "");
        value = replace(value, "(", "");
        value = replace(value, ")", "");
        value = replace(value, "!", "");
        value = regexReplace(value, SEARCH_SEPARATORS, " ");
        value = regexReplace(value, WHITESPACE, " ");
        return "TRIM(LOWER(" + value + "))";
    }

    private static String regexReplace(String expression, String pattern, String replacement) {
        return "REGEXP_REPLACE(" + expression + ", " + sqlLiteral(pattern) + ", " + sqlLiteral(replacement) + ")";
    }

    private static String replace(String expression, String target, String replacement) {
        return "REPLACE(" + expression + ", " + sqlLiteral(target) + ", " + sqlLiteral(replacement) + ")";
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }
}
