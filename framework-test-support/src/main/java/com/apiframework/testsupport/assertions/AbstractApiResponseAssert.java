package com.apiframework.testsupport.assertions;

import com.apiframework.json.JacksonProvider;
import com.apiframework.model.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.AbstractAssert;
import java.io.IOException;

/**
 * Generic AssertJ base class for {@link ApiResponse} assertions.
 *
 * <p>Allure steps are generated automatically — {@code AllureAspectJ} intercepts every public
 * method on {@code AbstractAssert} subclasses via LTW, so each fluent call produces a named step
 * in the Allure report without any manual {@code Allure.step()} annotation in this class.
 *
 * @param <SELF> the concrete assertion type (for fluent chaining)
 * @param <T>    the response body type
 */
public abstract class AbstractApiResponseAssert<SELF extends AbstractApiResponseAssert<SELF, T>, T>
        extends AbstractAssert<SELF, ApiResponse<T>> {

    private JsonNode parsedBody;

    protected AbstractApiResponseAssert(ApiResponse<T> actual, Class<SELF> selfType) {
        super(actual, selfType);
    }

    /**
     * Asserts that the HTTP status code equals {@code expected}.
     */
    public SELF hasStatus(int expected) {
        isNotNull();
        if (actual.statusCode() != expected) {
            failWithMessage("Expected status <%d> but was <%d>", expected, actual.statusCode());
        }
        return myself;
    }

    /**
     * Parses the raw JSON response body and returns a {@link BodyAssert} for fluent
     * field-level and contract assertions.
     *
     * <p>The raw body is parsed at most once per assert instance — subsequent calls reuse
     * the cached {@link JsonNode}.
     *
     * <p>Schema and snapshot validation are available on {@link BodyAssert} and always
     * target the full original response JSON.
     */
    public BodyAssert body() {
        isNotNull();

        if (actual.rawBody().isBlank()) {
            failWithMessage("Response body is empty, cannot parse JSON");
        }

        try {
            if (parsedBody == null) {
                parsedBody = JacksonProvider
                        .defaultMapper()
                        .readTree(actual.rawBody());
            }
        } catch (IOException e) {
            failWithMessage(
                    "Failed to parse response body as JSON: %s",
                    e.getMessage()
            );
        }

        return BodyAssert.of(parsedBody, actual.rawBody());
    }
}
