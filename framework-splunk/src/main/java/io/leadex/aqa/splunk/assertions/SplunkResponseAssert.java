package io.leadex.aqa.splunk.assertions;

import io.leadex.aqa.splunk.model.SplunkSearchResponse;
import io.leadex.aqa.splunk.model.SplunkSearchRow;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;

public final class SplunkResponseAssert extends AbstractAssert<SplunkResponseAssert, SplunkSearchResponse> {

    SplunkResponseAssert(SplunkSearchResponse actual) {
        super(actual, SplunkResponseAssert.class);
    }

    public static SplunkResponseAssert assertThat(SplunkSearchResponse response) {
        return new SplunkResponseAssert(response);
    }

    public SplunkResponseAssert isNotEmpty() {
        requireResults();
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

    /**
     * Navigates to the first search result and runs {@code asserts} against a
     * {@link SplunkRowAssert} inside that scope. Returns {@code this} for chaining.
     *
     * <p>Running the assertions inside the lambda scope makes the navigation a collapsible
     * Allure step that nests its per-result children — the same shape
     * {@code ApiResponseAssert.body(Consumer)} gives the JSON body.
     *
     * <p>One of the two ways into a row (the other is
     * {@link #matching(java.util.function.Predicate, java.util.function.Consumer)}): the DSL has no
     * flat, non-scoped navigation, so the report never renders a navigation and its leaves as
     * siblings.
     */
    public SplunkResponseAssert first(Consumer<SplunkRowAssert> asserts) {
        requireResults();
        asserts.accept(new SplunkRowAssert(actual.results().get(0)));
        return this;
    }

    /**
     * Navigates to the <em>first</em> search result that satisfies {@code predicate} and runs
     * {@code asserts} against a {@link SplunkRowAssert} inside that scope. Returns {@code this} for
     * chaining.
     *
     * <p>The content selector. A log assertion usually knows what the row says, not where it sits
     * ("the event carrying this correlationId"), so selecting by predicate states what {@code first()}
     * would otherwise assume — it removes the "row 0 happens to be the row I want" trust from the
     * test and makes a miss fail loudly instead of asserting against the wrong event.
     *
     * <p>Fails when nothing matches. When several rows match, the first match in response order wins
     * (documented, not incidental) — narrow the search, or assert on the row's fields inside the
     * scope, if the match set matters. Note that {@link SplunkSearchRow#field(String)} returns
     * {@code null} for a field Splunk did not return, so write predicates null-safely
     * ({@code "OUT".equals(row.field("tracePoint"))}) — a bare {@code row.field(…).equals(…)} throws
     * NPE inside the predicate instead of failing the assertion.
     */
    public SplunkResponseAssert matching(
            Predicate<SplunkSearchRow> predicate, Consumer<SplunkRowAssert> asserts) {
        asserts.accept(new SplunkRowAssert(requireMatch(predicate)));
        return this;
    }

    /*
     * Shared preconditions. Private on purpose: the aspect's pointcut is
     * execution(public * AbstractAssert+.*(..)), so a private helper is not woven and the guard stops
     * rendering as a user-visible step — calling isNotEmpty() from first() puts a second
     * "splunk response is not empty" row next to the caller's own explicit one.
     */
    private void requireResults() {
        isNotNull();
        if (actual.isEmpty()) {
            failWithMessage("Expected Splunk response to contain results, but it was empty");
        }
    }

    private SplunkSearchRow requireMatch(Predicate<SplunkSearchRow> predicate) {
        requireResults();
        Optional<SplunkSearchRow> match = actual.results().stream().filter(predicate).findFirst();
        if (match.isEmpty()) {
            failWithMessage(
                "Expected at least one Splunk result to match the given predicate, but none of <%d> results did",
                actual.size());
        }
        return match.get();
    }
}
