package io.leadex.aqa.testsupport.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import io.leadex.aqa.testsupport.contracts.JsonSchemaContractValidator;
import io.leadex.aqa.testsupport.contracts.SnapshotContractValidator;
import org.assertj.core.api.AbstractAssert;

import java.util.function.Consumer;

/**
 * Fluent assertions over a parsed JSON response body.
 *
 * <p>Obtained via {@code ApiResponseAssert.assertThat(response).body(b -> ...)}.
 * All public methods are intercepted by {@code AllureAspectJ} via LTW — no manual
 * {@code Allure.step()} calls are needed.
 *
 * <p>Navigation methods ({@link #first(Consumer)}, {@link #at(int, Consumer)},
 * {@link #field(String, Consumer)}) run their assertions inside a lambda scope, producing nested
 * Allure steps. They never throw on their own. Assertions ({@link #isNotEmpty()},
 * {@link #hasField(String)}) fail with explicit messages. Field-level assertions fail inside
 * {@link FieldAssert}.
 *
 * <p>{@link #matchesSchema(String)} and {@link #matchesSnapshot(String)} always operate on
 * {@code rawBody} — the full original response JSON — regardless of how deep navigation has gone.
 * They are intentionally absent from {@link FieldAssert} to prevent ambiguous sub-node validation.
 */
public final class BodyAssert extends AbstractAssert<BodyAssert, JsonNode> {

    private static final SnapshotContractValidator SNAPSHOT_VALIDATOR = new SnapshotContractValidator();
    private static final JsonSchemaContractValidator SCHEMA_VALIDATOR = new JsonSchemaContractValidator();

    /** Full original response JSON — never changes through navigation. */
    private final String rawBody;

    BodyAssert(JsonNode node, String rawBody) {
        super(node, BodyAssert.class);
        this.rawBody = rawBody;
    }

    static BodyAssert of(JsonNode node, String rawBody) {
        return new BodyAssert(node, rawBody);
    }

    // ── Array navigation ────────────────────────────────────────────────────

    /**
     * Asserts the body is a non-empty JSON array, then runs {@code asserts} against a new
     * {@code BodyAssert} scoped to the element at {@code index}. Returns {@code this} for chaining.
     *
     * <p>{@code rawBody} is passed through unchanged — schema/snapshot validation always
     * targets the full response.
     */
    public BodyAssert at(int index, Consumer<BodyAssert> asserts) {
        isNotNull();
        if (!actual.isArray() || actual.size() <= index) {
            failWithMessage(
                "Expected array with index <%d> but size was <%d>",
                index, actual.isArray() ? actual.size() : 0
            );
        }
        asserts.accept(new BodyAssert(actual.get(index), rawBody));
        return this;
    }

    /**
     * Shortcut for {@link #at(int, Consumer) at(0, asserts)}.
     */
    public BodyAssert first(Consumer<BodyAssert> asserts) {
        return at(0, asserts);
    }

    // ── Field navigation ────────────────────────────────────────────────────

    /**
     * Navigates to a field using dot-notation (e.g. {@code "country.isoCode"}) and runs
     * {@code asserts} against a {@link FieldAssert} for value-level assertions. Returns
     * {@code this} for chaining.
     *
     * <p>Navigation is tolerant — a missing intermediate node produces a {@code MissingNode}
     * and does not throw. The terminal assertion method on {@link FieldAssert} is what fails.
     */
    public BodyAssert field(String dotPath, Consumer<FieldAssert> asserts) {
        isNotNull();
        JsonNode node = navigate(dotPath);
        asserts.accept(new FieldAssert(node, dotPath));
        return this;
    }

    /**
     * Asserts that the field at {@code dotPath} exists and is non-null.
     *
     * <p>Use this as an explicit structural check. For value assertions, use
     * {@link #field(String)} followed by a terminal assertion on {@link FieldAssert}.
     */
    public BodyAssert hasField(String dotPath) {
        isNotNull();
        JsonNode node = navigate(dotPath);
        if (node.isMissingNode() || node.isNull()) {
            failWithMessage("Expected field '%s' to exist but was %s",
                dotPath, node.isMissingNode() ? "missing" : "null");
        }
        return this;
    }

    // ── Body-level assertions ────────────────────────────────────────────────

    /**
     * Asserts that the body is a non-empty JSON array.
     */
    public BodyAssert isNotEmpty() {
        isNotNull();
        if (!actual.isArray()) {
            failWithMessage("Expected array body but was: %s", actual.getNodeType());
        }
        if (actual.isEmpty()) {
            failWithMessage("Expected non-empty array but was empty");
        }
        return this;
    }

    // ── Contract validation ──────────────────────────────────────────────────

    /**
     * Validates the <b>full original response</b> against the JSON Schema at
     * {@code classpathSchemaPath}.
     *
     * <p>Always uses {@code rawBody} — not the current navigation node.
     */
    public BodyAssert matchesSchema(String classpathSchemaPath) {
        SCHEMA_VALIDATOR.assertMatchesSchema(rawBody, classpathSchemaPath);
        return this;
    }

    /**
     * Validates the <b>full original response</b> against the golden-file snapshot named
     * {@code snapshotName}.
     *
     * <p>Always uses {@code rawBody} — not the current navigation node.
     */
    public BodyAssert matchesSnapshot(String snapshotName) {
        SNAPSHOT_VALIDATOR.assertMatchesSnapshot(snapshotName, rawBody);
        return this;
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private JsonNode navigate(String dotPath) {
        JsonNode current = actual;
        for (String key : dotPath.split("\\.")) {
            if (current.isArray() && isNonNegativeInteger(key)) {
                current = current.path(Integer.parseInt(key));
            } else {
                current = current.path(key);
            }
        }
        return current;
    }

    private static boolean isNonNegativeInteger(String key) {
        if (key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) return false;
        }
        return true;
    }
}
