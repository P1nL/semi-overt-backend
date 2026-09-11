package com.platform.search.model;

import java.util.List;

/**
 * Normalized and bounded search input shared by article and user queries.
 * LIKE patterns are generated here so callers cannot accidentally re-enable
 * wildcard semantics for an untrusted keyword.
 */
public final class SearchKeyword {

    private final String phrase;
    private final List<SearchTerm> terms;
    private final boolean phraseContentMatchAllowed;

    public SearchKeyword(String phrase, List<SearchTerm> terms, boolean phraseContentMatchAllowed) {
        this.phrase = phrase == null ? "" : phrase;
        this.terms = terms == null ? List.of() : List.copyOf(terms);
        this.phraseContentMatchAllowed = phraseContentMatchAllowed;
    }

    public String getPhrase() {
        return phrase;
    }

    public List<SearchTerm> getTerms() {
        return terms;
    }

    public boolean isBlank() {
        return phrase.isBlank();
    }

    public boolean isMultiTerm() {
        return terms.size() > 1;
    }

    public boolean isPhraseContentMatchAllowed() {
        return phraseContentMatchAllowed;
    }

    public String getPhraseLike() {
        return "%" + escapeLike(phrase) + "%";
    }

    public String getPhrasePrefixLike() {
        return escapeLike(phrase) + "%";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    public static final class SearchTerm {

        private final String value;
        private final boolean contentMatchAllowed;

        public SearchTerm(String value, boolean contentMatchAllowed) {
            this.value = value == null ? "" : value;
            this.contentMatchAllowed = contentMatchAllowed;
        }

        public String getValue() {
            return value;
        }

        public boolean isContentMatchAllowed() {
            return contentMatchAllowed;
        }

        public String getLike() {
            return "%" + escapeLike(value) + "%";
        }
    }
}