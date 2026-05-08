package com.apiframework.splunk;

// Pre-built SPL queries for domain-specific patterns.
// Keeps field names defined in one place — changes to Splunk log format require edits here only.
// Use SplunkQueryBuilder directly for queries that don't fit these patterns.
public final class SplunkQueries {

    private SplunkQueries() {}

    // Standard correlation ID search: searches index for correlationId, extracts JSON fields
    // via spath, and returns a table of the standard log fields.
    public static String forCorrelationId(String corrId, String index) {
        return SplunkQueryBuilder.search()
            .index(index)
            .where("correlationId", corrId)
            .pipe("spath")
            .pipe("table correlationId, message, tracePoint, applicationName, timestamp")
            .build();
    }

    // Same as above but with a custom table field list. Use when the standard fields are insufficient.
    public static String forCorrelationId(String corrId, String index, String tableFields) {
        return SplunkQueryBuilder.search()
            .index(index)
            .where("correlationId", corrId)
            .pipe("spath")
            .pipe("table " + tableFields)
            .build();
    }
}
