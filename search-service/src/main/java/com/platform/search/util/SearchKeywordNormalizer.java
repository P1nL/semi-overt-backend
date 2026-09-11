package com.platform.search.util;

import com.platform.search.model.SearchKeyword;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded keyword normalization matching the approved monolith search
 * semantics. At most six distinct terms are allowed into generated SQL.
 */
public final class SearchKeywordNormalizer {

    private static final Pattern ARTICLE_WORD_SEPARATOR_PATTERN = Pattern.compile("[-_./|]+");
    private static final Pattern USER_WORD_SEPARATOR_PATTERN = Pattern.compile("[-_./]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final int MAX_TERMS = 6;

    private SearchKeywordNormalizer() {
    }

    public static SearchKeyword article(String value) {
        return build(value, ARTICLE_WORD_SEPARATOR_PATTERN, true);
    }

    public static SearchKeyword user(String value) {
        return build(value, USER_WORD_SEPARATOR_PATTERN, false);
    }

    public static String normalizeArticle(String value) {
        return normalize(value, ARTICLE_WORD_SEPARATOR_PATTERN);
    }

    public static String normalizeUser(String value) {
        return normalize(value, USER_WORD_SEPARATOR_PATTERN);
    }

    private static SearchKeyword build(String value, Pattern separators, boolean allowContentTerms) {
        String phrase = normalize(value, separators);
        if (phrase.isBlank()) {
            return new SearchKeyword("", List.of(), false);
        }

        Set<String> uniqueTerms = new LinkedHashSet<>();
        for (String part : phrase.split(" ")) {
            if (!part.isBlank()) {
                uniqueTerms.add(part);
                if (uniqueTerms.size() >= MAX_TERMS) {
                    break;
                }
            }
        }

        List<SearchKeyword.SearchTerm> terms = uniqueTerms.stream()
                .map(term -> new SearchKeyword.SearchTerm(term,
                        allowContentTerms && allowsContentMatch(term)))
                .toList();
        return new SearchKeyword(phrase, terms, allowContentTerms && allowsContentMatch(phrase));
    }

    public static String normalize(String value, Pattern separators) {
        if (value == null) {
            return "";
        }
        String lowered = separators.matcher(value).replaceAll(" ")
                .toLowerCase(Locale.ROOT)
                .trim();
        return lowered.isBlank() ? "" : WHITESPACE_PATTERN.matcher(lowered).replaceAll(" ");
    }

    /** Single ASCII letters/digits are useful in titles but noisy in body text. */
    public static boolean allowsContentMatch(String value) {
        return value != null
                && !(value.length() == 1
                && value.charAt(0) <= 0x7f
                && Character.isLetterOrDigit(value.charAt(0)));
    }
}