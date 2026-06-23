package io.leadex.aqa.testsupport.assertions;

import io.leadex.aqa.json.JacksonProvider;
import io.leadex.aqa.model.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.AbstractAssert;
import java.io.IOException;
import java.util.function.Consumer;

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
     * Parses the raw JSON response body and runs {@code asserts} against a {@link BodyAssert}
     * for fluent field-level and contract assertions. Returns {@code myself} for chaining.
     *
     * <p>Running the assertions inside the lambda scope makes the body block a collapsible
     * Allure step that nests its field/contract children.
     *
     * <p>The raw body is parsed at most once per assert instance — subsequent calls reuse
     * the cached {@link JsonNode}.
     *
     * <p>Schema and snapshot validation are available on {@link BodyAssert} and always
     * target the full original response JSON.
     */
    public SELF body(Consumer<BodyAssert> asserts) {
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

        asserts.accept(BodyAssert.of(parsedBody, actual.rawBody()));
        return myself;
    }
}
