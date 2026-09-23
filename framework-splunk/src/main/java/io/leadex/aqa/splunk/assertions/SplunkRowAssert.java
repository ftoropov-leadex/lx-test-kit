package io.leadex.aqa.splunk.assertions;

import io.leadex.aqa.splunk.model.SplunkSearchRow;
import org.assertj.core.api.AbstractAssert;

public final class SplunkRowAssert extends AbstractAssert<SplunkRowAssert, SplunkSearchRow> {

    SplunkRowAssert(SplunkSearchRow actual) {
        super(actual, SplunkRowAssert.class);
    }

    public SplunkRowAssert hasField(String name, String expectedValue) {
        isNotNull();
        String actualValue = actual.field(name);
        if (!expectedValue.equals(actualValue)) {
            failWithMessage("Expected field <%s> to be <%s>, but was <%s>", name, expectedValue, actualValue);
        }
        return this;
    }

    public SplunkRowAssert fieldContains(String name, String substring) {
        isNotNull();
        String value = actual.field(name);
        if (value == null || !value.contains(substring)) {
            failWithMessage("Expected field <%s> to contain <%s>, but was <%s>", name, substring, value);
        }
        return this;
    }

    public SplunkRowAssert rawContains(String substring) {
        isNotNull();
        if (actual.raw() == null || !actual.raw().contains(substring)) {
            failWithMessage("Expected _raw to contain <%s>, but was <%s>", substring, actual.raw());
        }
        return this;
    }

    public SplunkRowAssert hasSource(String expected) {
        isNotNull();
        if (!expected.equals(actual.source())) {
            failWithMessage("Expected source to be <%s>, but was <%s>", expected, actual.source());
        }
        return this;
    }

    public SplunkRowAssert hasHost(String expected) {
        isNotNull();
        if (!expected.equals(actual.host())) {
            failWithMessage("Expected host to be <%s>, but was <%s>", expected, actual.host());
        }
        return this;
    }
}
