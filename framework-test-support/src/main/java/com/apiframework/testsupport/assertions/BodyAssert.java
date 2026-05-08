package com.apiframework.testsupport.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import com.apiframework.testsupport.contracts.JsonSchemaContractValidator;
import com.apiframework.testsupport.contracts.SnapshotContractValidator;
import org.assertj.core.api.AbstractAssert;

/**
 * Fluent assertions over a parsed JSON response body.
 *
 * <p>Obtained via {@code ApiResponseAssert.assertThat(response).body()}.
 * All public methods are intercepted by {@code AllureAspectJ} via LTW — no manual
 * {@code Allure.step()} calls are needed.
 *
 * <p>Navigation methods ({@link #first()}, {@link #at(int)}, {@link #field(String)}) never throw
 * on their own. Assertions ({@link #isNotEmpty()}, {@link #hasField(String)}) fail with explicit
 * messages. Field-level assertions fail inside {@link FieldAssert}.
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
     * Asserts the body is a non-empty JSON array, then returns a new {@code BodyAssert}
     * scoped to the element at {@code index}.
     *
     * <p>{@code rawBody} is passed through unchanged — schema/snapshot validation always
     * targets the full response.
     */
    public BodyAssert at(int index) {
        isNotNull();
        if (!actual.isArray() || actual.size() <= index) {
            failWithMessage(
                "Expected array with index <%d> but size was <%d>",
                index, actual.isArray() ? actual.size() : 0
            );
        }
        return new BodyAssert(actual.get(index), rawBody);
    }

    /**
     * Shortcut for {@link #at(int) at(0)}.
     */
    public BodyAssert first() {
        return at(0);
    }

    // ── Field navigation ────────────────────────────────────────────────────

    /**
     * Navigates to a field using dot-notation (e.g. {@code "country.isoCode"}) and returns
     * a {@link FieldAssert} for value-level assertions.
     *
     * <p>Navigation is tolerant — a missing intermediate node produces a {@code MissingNode}
     * and does not throw. The terminal assertion method on {@link FieldAssert} is what fails.
     */
    public FieldAssert field(String dotPath) {
        isNotNull();
        JsonNode node = navigate(dotPath);
        return new FieldAssert(node, this, dotPath);
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
            current = current.path(key);
        }
        return current;
    }
}
