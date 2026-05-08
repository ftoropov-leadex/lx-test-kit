package com.apiframework.splunk.assertions;

import com.apiframework.splunk.model.SplunkSearchResult;
import org.assertj.core.api.AbstractAssert;

public class SplunkResultAssert extends AbstractAssert<SplunkResultAssert, SplunkSearchResult> {

    protected SplunkResultAssert(SplunkSearchResult actual) {
        super(actual, SplunkResultAssert.class);
    }

    public static SplunkResultAssert assertThat(SplunkSearchResult result) {
        return new SplunkResultAssert(result);
    }

    public SplunkResultAssert hasField(String name, String expectedValue) {
        isNotNull();
        String actualValue = actual.field(name);
        if (!expectedValue.equals(actualValue)) {
            failWithMessage("Expected field <%s> to be <%s>, but was <%s>", name, expectedValue, actualValue);
        }
        return this;
    }

    public SplunkResultAssert fieldContains(String name, String substring) {
        isNotNull();
        String value = actual.field(name);
        if (value == null || !value.contains(substring)) {
            failWithMessage("Expected field <%s> to contain <%s>, but was <%s>", name, substring, value);
        }
        return this;
    }

    public SplunkResultAssert rawContains(String substring) {
        isNotNull();
        if (actual.raw() == null || !actual.raw().contains(substring)) {
            failWithMessage("Expected _raw to contain <%s>, but was <%s>", substring, actual.raw());
        }
        return this;
    }

    public SplunkResultAssert hasSource(String expected) {
        isNotNull();
        if (!expected.equals(actual.source())) {
            failWithMessage("Expected source to be <%s>, but was <%s>", expected, actual.source());
        }
        return this;
    }

    public SplunkResultAssert hasHost(String expected) {
        isNotNull();
        if (!expected.equals(actual.host())) {
            failWithMessage("Expected host to be <%s>, but was <%s>", expected, actual.host());
        }
        return this;
    }
}
