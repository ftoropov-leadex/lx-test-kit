package com.apiframework.splunk.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// Ordered list of Splunk search results with chainable filter and accessor helpers.
// Returned by all search and await operations on SplunkClient.
public record SplunkSearchResponse(List<SplunkSearchResult> results) {

    // True when the search produced no results.
    public boolean isEmpty() {
        return results.isEmpty();
    }

    // Number of results in this response.
    public int size() {
        return results.size();
    }

    // Returns a new response containing only the results that match the predicate.
    public SplunkSearchResponse filter(Predicate<SplunkSearchResult> predicate) {
        List<SplunkSearchResult> filtered = results.stream()
            .filter(predicate)
            .collect(Collectors.toList());
        return new SplunkSearchResponse(filtered);
    }

    // Narrows results to those whose source field matches (case-insensitive).
    public SplunkSearchResponse filterBySource(String source) {
        return filter(result -> source.equalsIgnoreCase(result.source()));
    }

    // Narrows results to those whose host field matches (case-insensitive).
    public SplunkSearchResponse filterByHost(String host) {
        return filter(result -> host.equalsIgnoreCase(result.host()));
    }

    // Narrows results to those where the named field equals fieldValue.
    public SplunkSearchResponse filterByField(String fieldName, String fieldValue) {
        return filter(result -> fieldValue.equals(result.field(fieldName)));
    }

    // Returns the first result, or empty if no results exist.
    public Optional<SplunkSearchResult> first() {
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

}
