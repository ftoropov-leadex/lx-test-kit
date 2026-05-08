package com.apiframework.splunk;

import java.util.ArrayList;
import java.util.List;

// Fluent builder for SPL (Search Processing Language) queries.
// Eliminates string concatenation errors; always produces a "search ..." string.
// Rules: string-first, no validation, no AST. Use pipe() for anything not covered by named methods.
public final class SplunkQueryBuilder {

    private String index;
    private String source;
    private String sourceType;
    private String host;
    private final List<String> searchTerms;
    private final List<String> pipeClauses;

    private SplunkQueryBuilder() {
        this.searchTerms = new ArrayList<>();
        this.pipeClauses = new ArrayList<>();
    }

    // Entry point. Returns a fresh builder; the query will start with "search ".
    public static SplunkQueryBuilder search() {
        return new SplunkQueryBuilder();
    }

    // Adds index=<value> to the search clause.
    public SplunkQueryBuilder index(String index) {
        this.index = index;
        return this;
    }

    // Adds source="<value>" to the search clause.
    public SplunkQueryBuilder source(String source) {
        this.source = source;
        return this;
    }

    // Adds sourcetype="<value>" to the search clause.
    public SplunkQueryBuilder sourceType(String sourceType) {
        this.sourceType = sourceType;
        return this;
    }

    // Adds host="<value>" to the search clause.
    public SplunkQueryBuilder host(String host) {
        this.host = host;
        return this;
    }

    // Adds fieldName="fieldValue" to the search clause. Value is SPL-escaped.
    public SplunkQueryBuilder where(String fieldName, String fieldValue) {
        searchTerms.add(fieldName + "=\"" + escapeQuotes(fieldValue) + "\"");
        return this;
    }

    // Adds a quoted literal keyword to the search clause, e.g. "ERROR".
    public SplunkQueryBuilder keyword(String term) {
        searchTerms.add("\"" + escapeQuotes(term) + "\"");
        return this;
    }

    // Appends | spath output=<path> path=<path>. Extracts a JSON field from _raw.
    public SplunkQueryBuilder spath(String path) {
        return spath(path, path);
    }

    // Appends | spath output=<outputField> path=<path>. Use when the output name differs from the path.
    public SplunkQueryBuilder spath(String path, String outputField) {
        pipeClauses.add("spath output=" + outputField + " path=" + path);
        return this;
    }

    // Appends | rex field=_raw "<regexPattern>". Use named capture groups to extract fields.
    public SplunkQueryBuilder rex(String regexPattern) {
        return rex("_raw", regexPattern);
    }

    // Appends | rex field=<field> "<regexPattern>". Extracts from a specific field instead of _raw.
    public SplunkQueryBuilder rex(String field, String regexPattern) {
        pipeClauses.add("rex field=" + field + " \"" + regexPattern + "\"");
        return this;
    }

    // Appends | <splFragment>. Escape hatch for any pipe command not covered by named methods.
    public SplunkQueryBuilder pipe(String splFragment) {
        pipeClauses.add(splFragment);
        return this;
    }

    // Appends a raw SPL fragment to the search clause without quoting or a pipe prefix.
    public SplunkQueryBuilder raw(String splFragment) {
        searchTerms.add(splFragment);
        return this;
    }

    // Assembles and returns the final SPL string. Order: search, index, source, sourcetype,
    // host, where/keyword terms, then pipe clauses.
    public String build() {
        StringBuilder sb = new StringBuilder("search ");
        if (index != null)      sb.append("index=").append(index).append(' ');
        if (source != null)     sb.append("source=\"").append(escapeQuotes(source)).append("\" ");
        if (sourceType != null) sb.append("sourcetype=\"").append(escapeQuotes(sourceType)).append("\" ");
        if (host != null)       sb.append("host=\"").append(escapeQuotes(host)).append("\" ");
        for (String term : searchTerms)   sb.append(term).append(' ');
        for (String clause : pipeClauses) sb.append("| ").append(clause).append(' ');
        return sb.toString().trim();
    }

    // Escapes backslashes and double-quotes so values are safe inside SPL-quoted strings.
    private static String escapeQuotes(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
