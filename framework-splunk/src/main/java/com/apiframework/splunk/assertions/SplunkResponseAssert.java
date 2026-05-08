package com.apiframework.splunk.assertions;

import com.apiframework.splunk.model.SplunkSearchResponse;
import com.apiframework.splunk.model.SplunkSearchResult;
import org.assertj.core.api.AbstractAssert;

public class SplunkResponseAssert extends AbstractAssert<SplunkResponseAssert, SplunkSearchResponse> {

    protected SplunkResponseAssert(SplunkSearchResponse actual) {
        super(actual, SplunkResponseAssert.class);
    }

    public static SplunkResponseAssert assertThat(SplunkSearchResponse response) {
        return new SplunkResponseAssert(response);
    }

    public SplunkResponseAssert isNotEmpty() {
        isNotNull();
        if (actual.isEmpty()) {
            failWithMessage("Expected Splunk response to contain results, but it was empty");
        }
        return this;
    }

    public SplunkResponseAssert hasResultCount(int expected) {
        isNotNull();
        if (actual.size() != expected) {
            failWithMessage("Expected Splunk response to contain <%d> results, but found <%d>",
                expected, actual.size());
        }
        return this;
    }

    public SplunkResponseAssert anyResultHasField(String fieldName, String expectedValue) {
        isNotNull();
        boolean found = actual.results().stream()
            .anyMatch(r -> expectedValue.equals(r.field(fieldName)));
        if (!found) {
            failWithMessage(
                "Expected at least one Splunk result to have field <%s> = <%s>, but none did",
                fieldName, expectedValue);
        }
        return this;
    }

    public SplunkResultAssert first() {
        isNotEmpty();
        return new SplunkResultAssert(actual.results().get(0));
    }

    public SplunkResultAssert result(int index) {
        isNotNull();
        if (index < 0 || index >= actual.size()) {
            failWithMessage(
                "Expected Splunk response to have result at index <%d>, but size was <%d>",
                index, actual.size());
        }
        return new SplunkResultAssert(actual.results().get(index));
    }
}
